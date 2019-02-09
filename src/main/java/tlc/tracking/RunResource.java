package tlc.tracking;

import com.google.cloud.datastore.*;
import org.restlet.data.Form;
import org.restlet.data.Parameter;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

import java.lang.reflect.Array;
import java.util.*;

/*
 * Get a token to test your application locally with datastore:
 * https://cloud.google.com/docs/authentication/production
 *
 *   1. Create a new service account with role owner https://console.cloud.google.com/apis/credentials/serviceaccountkey
 *   2. Download the key, we will refer to the path to the key as [PATH]
 *   3. Run: export GOOGLE_APPLICATION_CREDENTIALS="[PATH]"
 *   4. Run in the same terminal (not in your IDE): mvn appengine:run
 *
 *   Note: The id property is not the key, it represents the Run identifier. So, many entities can have the same id
 *   It just means that they are part of the same run.
 */


public class RunResource extends ServerResource {
	
	private Datastore datastore;
	private KeyFactory recordsKey;
	
	public RunResource() {
		datastore = DatastoreOptions.getDefaultInstance().getService();
		recordsKey = datastore.newKeyFactory().setKind("record");
	}
	
	/*
	 * Enable you to convert a List object in an Array
	 * It will help you pass lists to variadic functions
	 *
	 * In an alternative world where datastore.put() accepts Strings instead of Entities, we could write:
	 * List<String> places = new ArrayList<>();
	 * places.add("Buenos Aires");
	 * places.add("Córdoba");
	 * places.add("La Plata");
	 * String[] placesArr = batch(String.class, places);
	 * datastore.put(placesArr);
	 *
	 * You might need this function to do:
	 *   1. batch operations with datastore.put()
	 *   2. batch operations with datastore.delete()
	 *   3. dynamically build CompositeFilter.and() (you must add some logic however,
	 *      as "and" takes a fixed parameters before its vararg parameter - be clever :D)
	 */
	private static <T> T[] batch(Class<T> c, List<T> g) {
		@SuppressWarnings("unchecked")
		T[] res = (T[]) Array.newInstance(c, g.size());
		g.toArray(res);
		return res;
	}
	
	@Post("json")
	public void bulkAdd(RecordList toAdd) {
		/*
		 * Doc that might help you:
		 * https://cloud.google.com/datastore/docs/concepts/entities#creating_an_entity
		 */
		for (Record r : toAdd) {
			Key recKey = datastore.allocateId(recordsKey.newKey());
			Entity record = Entity
					.newBuilder(recKey)
					.set("id", r.id)
					.set("lat", r.lat)
					.set("lon", r.lon)
					.set("user", r.user)
					.set("timestamp", r.timestamp)
					.build();
			datastore.put(record);
		}
	}
	
	@Get("json")
	public RecordList search() {
		/*
		 * Doc that might help you:
		 * https://cloud.google.com/datastore/docs/concepts/queries#composite_filters
		 * https://cloud.google.com/datastore/docs/concepts/indexes#index_configuration
		 * Check also src/main/webapp/WEB-INF/datastore-indexes.xml
		 */
		
		// Read and print URL parameters
		Form form = getRequest().getResourceRef().getQueryAsForm();
		//Query q1 = Query.newEntityQueryBuilder().setFilter(null);
		
		List<StructuredQuery.PropertyFilter> filters = new ArrayList<>();
		for (Parameter parameter : form) {
			switch (parameter.getName()) {
				case "timestamp" :
					if(parameter.getValue().contains(",")) {
						String[] values = parameter.getValue().split(",");
						filters.add(StructuredQuery.PropertyFilter.ge(parameter.getName(), Long.parseLong(values[0])));
						filters.add(StructuredQuery.PropertyFilter.le(parameter.getName(), Long.parseLong(values[1])));
					} else {
						filters.add(StructuredQuery.PropertyFilter.eq(parameter.getName(), Long.parseLong(parameter.getValue())));
					}
					break;
				case "id" :
					if(parameter.getValue().contains(",")) {
						String[] values = parameter.getValue().split(",");
						filters.add(StructuredQuery.PropertyFilter.ge(parameter.getName(), Long.parseLong(values[0])));
						filters.add(StructuredQuery.PropertyFilter.le(parameter.getName(), Long.parseLong(values[1])));
					} else {
						filters.add(StructuredQuery.PropertyFilter.eq(parameter.getName(), Long.parseLong(parameter.getValue())));
					}
					break;
				case "lat" :
					if(parameter.getValue().contains(",")) {
						String[] values = parameter.getValue().split(",");
						filters.add(StructuredQuery.PropertyFilter.ge(parameter.getName(), Double.parseDouble(values[0])));
						filters.add(StructuredQuery.PropertyFilter.le(parameter.getName(), Double.parseDouble(values[1])));
					} else {
						filters.add(StructuredQuery.PropertyFilter.eq(parameter.getName(), Double.parseDouble(parameter.getValue())));
					}
					break;
				case "lon" :
					if(parameter.getValue().contains(",")) {
						String[] values = parameter.getValue().split(",");
						filters.add(StructuredQuery.PropertyFilter.ge(parameter.getName(), Double.parseDouble(values[0])));
						filters.add(StructuredQuery.PropertyFilter.le(parameter.getName(), Double.parseDouble(values[1])));
					} else {
						filters.add(StructuredQuery.PropertyFilter.eq(parameter.getName(), Double.parseDouble(parameter.getValue())));
					}
					break;
				default :
					filters.add(StructuredQuery.PropertyFilter.eq(parameter.getName(), parameter.getValue()));
			}
			System.out.print("parameter " + parameter.getName());
			System.out.println(" -> " + parameter.getValue());
		}
		
		EntityQuery.Builder eqb = Query.newEntityQueryBuilder().setKind("record");
		if(!filters.isEmpty()) {
			if(filters.size() == 1) {
				eqb.setFilter(filters.get(0));
			} else {
				eqb.setFilter(StructuredQuery.CompositeFilter.and(filters.get(0), batch(StructuredQuery.PropertyFilter.class, filters.subList(1, filters.size() -1)))).build();
			}
		}
		
		Query q1 = eqb.build();
		
		RecordList res = new RecordList();
		for (QueryResults<Entity> it = datastore.run(q1); it.hasNext(); ) {
			Entity r = it.next();
			res.add(new Record(
					(int) r.getLong("id"),
					r.getDouble("lat"),
					r.getDouble("lon"),
					r.getString("user"),
					r.getLong("timestamp")));
		}
		
		//@FIXME You must query Google Datastore to retrieve the records instead of sending dummy results
		//@FIXME Don't forget to apply potential filters got from the URL parameters
		
		return res;
	}
	
	@Delete("json")
	public void bulkDelete() {
		/*
		 * Doc that might help you:
		 * https://cloud.google.com/datastore/docs/concepts/entities#deleting_an_entity
		 * You might to do one or more query before to get some keys...
		 */
		
		
		String[] run_ids = getRequest().getAttributes().get("list").toString().split(",");
		
		List<Key> listKey = new ArrayList<>();
		for (String r : run_ids) {
			Query q = Query.newEntityQueryBuilder().setKind("record").setFilter(StructuredQuery.PropertyFilter.eq("id", Integer.valueOf(r))).build();
			for (QueryResults<Entity> it = datastore.run(q); it.hasNext(); ) {
				Entity e = it.next();
				listKey.add(e.getKey());
			}
		}
		
		if(!listKey.isEmpty()) {
			datastore.delete(batch(Key.class, listKey));
		}
	}
}
