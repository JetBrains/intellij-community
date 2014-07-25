package org.jetbrains.io;

import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JsonUtil {
  public static <T> List<T> nextList(JsonReaderEx reader) {
    reader.beginArray();
    if (!reader.hasNext()) {
      reader.endArray();
      return Collections.emptyList();
    }

    List<T> list = new SmartList<T>();
    readListBody(reader, list);
    reader.endArray();
    return list;
  }

  public static Object[] nextArray(JsonReaderEx reader) {
    List<Object> list = nextList(reader);
    return list.toArray(new Object[list.size()]);
  }

  public static Map<String, Object> nextObject(JsonReaderEx reader) {
    Map<String, Object> map = new THashMap<String, Object>();
    reader.beginObject();
    while (reader.hasNext()) {
      map.put(reader.nextName(), nextAny(reader));
    }
    reader.endObject();
    return map;
  }

  @Nullable
  public static Object nextAny(JsonReaderEx reader)  {
    switch (reader.peek()) {
      case BEGIN_ARRAY:
        return nextList(reader);

      case BEGIN_OBJECT:
        return nextObject(reader);

      case STRING:
        return reader.nextString();

      case NUMBER:
        return reader.nextDouble();

      case BOOLEAN:
        return reader.nextBoolean();

      case NULL:
        reader.nextNull();
        return null;

      default: throw new IllegalStateException();
    }
  }

  public static <T> void readListBody(JsonReaderEx reader, List<T> list)  {
    do {
      //noinspection unchecked
      list.add((T)nextAny(reader));
    }
    while (reader.hasNext());
  }
}
