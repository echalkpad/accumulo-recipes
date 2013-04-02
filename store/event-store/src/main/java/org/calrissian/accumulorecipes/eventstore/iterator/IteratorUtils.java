package org.calrissian.accumulorecipes.eventstore.iterator;

import org.apache.accumulo.core.data.ByteSequence;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.iterators.SortedKeyValueIterator;
import org.apache.hadoop.io.Text;
import org.calrissian.accumulorecipes.eventstore.domain.Event;
import org.calrissian.commons.domain.Tuple;
import org.calrissian.commons.serialization.ObjectMapperContext;
import org.calrissian.mango.types.TypeContext;

import java.util.ArrayList;
import java.util.Collection;

import static org.calrissian.accumulorecipes.eventstore.support.Constants.DELIM;
import static org.calrissian.accumulorecipes.eventstore.support.Constants.DELIM_END;
import static org.calrissian.accumulorecipes.eventstore.support.Constants.SHARD_PREFIX_F;

public class IteratorUtils {

    public static Value retrieveFullEvent(String eventUUID, Key topKey, SortedKeyValueIterator<Key,Value> sourceItr) {

        Key key = topKey;

        Key startRangeKey = new Key(key.getRow(), new Text(SHARD_PREFIX_F +
                DELIM +
                eventUUID));
        Key stopRangeKey = new Key(key.getRow(), new Text(SHARD_PREFIX_F +
                DELIM +
                eventUUID + DELIM_END));

        Range eventRange = new Range(startRangeKey, stopRangeKey);


        long timestamp = 0;

        try {
            sourceItr.seek(eventRange, new ArrayList<ByteSequence>(), false);

            Collection<Tuple> tuples = new ArrayList<Tuple>();
            while(sourceItr.hasTop()) {

                Key nextKey = sourceItr.getTopKey();
                sourceItr.next();

                if(!nextKey.getColumnFamily().toString().endsWith(eventUUID)) {
                    break;
                }

                String[] keyValueDatatype = nextKey.getColumnQualifier().toString().split(DELIM);

                if(keyValueDatatype.length == 3) {

                    String tupleKey = keyValueDatatype[0];
                    String tupleType = keyValueDatatype[1];
                    Object tupleVal = TypeContext.getInstance().denormalize(keyValueDatatype[2], tupleType);

                    Tuple tuple = new Tuple(tupleKey, tupleVal, nextKey.getColumnVisibility().toString());
                    tuples.add(tuple);


                    timestamp = nextKey.getTimestamp();
                }
            }

            Event event = new Event(eventUUID, timestamp);

            if(tuples.size() > 0) {
                event.putAll(tuples);
                return new Value(ObjectMapperContext.getInstance().getObjectMapper().writeValueAsBytes(event));
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return new Value("".getBytes());
    }
}