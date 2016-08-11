package org.jetbrains.jsonProtocol;

import com.google.gson.stream.JsonToken;
import com.intellij.util.ArrayUtilRt;
import gnu.trove.TDoubleArrayList;
import gnu.trove.THashMap;
import gnu.trove.TIntArrayList;
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.JsonReaderEx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class JsonReaders {
  public static final ObjectFactory<String> STRING_OBJECT_FACTORY = new ObjectFactory<String>() {
    @Override
    public String read(JsonReaderEx reader) {
      return reader.nextString();
    }
  };

  private JsonReaders() {
  }

  public static <T> ObjectFactory<Map<String, T>> mapFactory(@NotNull ObjectFactory<T> valueFactory) {
    return new MapFactory<>(valueFactory);
  }

  private static void checkIsNull(JsonReaderEx reader, String fieldName) {
    if (reader.peek() == JsonToken.NULL) {
      throw new RuntimeException("Field is not nullable" + (fieldName == null ? "" : (": " + fieldName)));
    }
  }

  public static String readRawString(JsonReaderEx reader) {
    return reader.nextString(true);
  }

  public static Object readRawStringOrMap(JsonReaderEx reader) {
    if (reader.peek() == JsonToken.BEGIN_OBJECT) {
      return readMap(reader, null);
    }
    else {
      return reader.nextString(true);
    }
  }

  // Don't use Guava CaseFormat.*! ObjectWithURL must be converted to OBJECT_WITH_URL
  public static String convertRawEnumName(@NotNull String enumValue) {
    int n = enumValue.length();
    StringBuilder builder = new StringBuilder(n + 4);
    boolean prevIsLowerCase = false;
    for (int i = 0; i < n; i++) {
      char c = enumValue.charAt(i);
      if (c == '-' || c == ' ') {
        builder.append('_');
        continue;
      }

      if (Character.isUpperCase(c)) {
        // second check handle "CSPViolation" (transform to CSP_VIOLATION)
        if (prevIsLowerCase || (i != 0 && (i + 1) < n && Character.isLowerCase(enumValue.charAt(i + 1)))) {
          builder.append('_');
        }
        builder.append(c);
        prevIsLowerCase = false;
      }
      else {
        builder.append(Character.toUpperCase(c));
        prevIsLowerCase = true;
      }
    }
    return builder.toString();
  }

  public static <T extends Enum<T>> T readEnum(@NotNull JsonReaderEx reader, @NotNull Class<T> enumClass) {
    if (reader.peek() == JsonToken.NULL) {
      reader.skipValue();
      return null;
    }

    try {
      return Enum.valueOf(enumClass, convertRawEnumName(reader.nextString()));
    }
    catch (IllegalArgumentException ignored) {
      return Enum.valueOf(enumClass, "NO_ENUM_CONST");
    }
  }

  public static <T> List<T> readObjectArray(@NotNull JsonReaderEx reader, @NotNull ObjectFactory<T> factory) {
    if (reader.peek() == JsonToken.NULL) {
      reader.skipValue();
      return null;
    }

    reader.beginArray();
    if (!reader.hasNext()) {
      reader.endArray();
      return Collections.emptyList();
    }

    List<T> result = new ArrayList<>();
    do {
      result.add(factory.read(reader));
    }
    while (reader.hasNext());
    reader.endArray();
    return result;
  }

  public static <T> Map<String, T> readMap(@NotNull JsonReaderEx reader, @Nullable ObjectFactory<T> factory) {
    if (reader.peek() == JsonToken.NULL) {
      reader.skipValue();
      return null;
    }

    reader.beginObject();
    if (!reader.hasNext()) {
      reader.endObject();
      return Collections.emptyMap();
    }

    Map<String, T> map = new THashMap<>();
    while (reader.hasNext()) {
      if (factory == null) {
        //noinspection unchecked
        map.put(reader.nextName(), (T)read(reader));
      }
      else {
        map.put(reader.nextName(), factory.read(reader));
      }
    }
    reader.endObject();
    return map;
  }

  public static Object read(JsonReaderEx reader) {
    switch (reader.peek()) {
      case BEGIN_ARRAY:
        return nextList(reader);

      case BEGIN_OBJECT:
        reader.beginObject();
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

  public static Map<String, Object> nextObject(JsonReaderEx reader) {
    Map<String, Object> map = new THashMap<>();
    while (reader.hasNext()) {
      map.put(reader.nextName(), read(reader));
    }
    reader.endObject();
    return map;
  }

  public static <T> List<T> nextList(JsonReaderEx reader) {
    reader.beginArray();
    if (!reader.hasNext()) {
      reader.endArray();
      return Collections.emptyList();
    }

    List<T> list = new ArrayList<>();
    do {
      //noinspection unchecked
      list.add((T)read(reader));
    }
    while (reader.hasNext());
    reader.endArray();
    return list;
  }

  public static List<String> readRawStringArray(JsonReaderEx reader) {
    reader.beginArray();
    if (!reader.hasNext()) {
      reader.endArray();
      return Collections.emptyList();
    }

    List<String> list = new ArrayList<>();
    do {
      //noinspection unchecked
      list.add(reader.nextString(true));
    }
    while (reader.hasNext());
    reader.endArray();
    return list;
  }

  public static long[] readLongArray(JsonReaderEx reader) {
    checkIsNull(reader, null);
    reader.beginArray();
    if (!reader.hasNext()) {
      reader.endArray();
      return ArrayUtilRt.EMPTY_LONG_ARRAY;
    }

    TLongArrayList result = new TLongArrayList();
    do {
      result.add(reader.nextLong());
    }
    while (reader.hasNext());
    reader.endArray();
    return result.toNativeArray();
  }

  public static double[] readDoubleArray(JsonReaderEx reader) {
    checkIsNull(reader, null);
    reader.beginArray();
    if (!reader.hasNext()) {
      reader.endArray();
      return new double[]{0};
    }

    TDoubleArrayList result = new TDoubleArrayList();
    do {
      result.add(reader.nextDouble());
    }
    while (reader.hasNext());
    reader.endArray();
    return result.toNativeArray();
  }

  public static int[] readIntArray(JsonReaderEx reader) {
    checkIsNull(reader, null);
    reader.beginArray();
    if (!reader.hasNext()) {
      reader.endArray();
      return ArrayUtilRt.EMPTY_INT_ARRAY;
    }

    TIntArrayList result = new TIntArrayList();
    do {
      result.add(reader.nextInt());
    }
    while (reader.hasNext());
    reader.endArray();
    return result.toNativeArray();
  }

  public static List<StringIntPair> readIntStringPairs(JsonReaderEx reader) {
    checkIsNull(reader, null);
    reader.beginArray();
    if (!reader.hasNext()) {
      reader.endArray();
      return Collections.emptyList();
    }

    List<StringIntPair> result = new ArrayList<>();
    do {
      reader.beginArray();
      result.add(new StringIntPair(reader.nextInt(), reader.nextString()));
      reader.endArray();
    }
    while (reader.hasNext());
    reader.endArray();
    return result;
  }

  public static boolean findBooleanField(String name, JsonReaderEx reader) {
    reader.beginObject();
    while (reader.hasNext()) {
      if (reader.nextName().equals(name)) {
        return reader.nextBoolean();
      }
      else {
        reader.skipValue();
      }
    }
    return false;
  }
}