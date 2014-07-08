package org.jetbrains.jsonProtocol;

import com.google.gson.stream.JsonToken;
import com.intellij.util.ArrayUtilRt;
import gnu.trove.TDoubleArrayList;
import gnu.trove.THashMap;
import gnu.trove.TIntArrayList;
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.io.JsonReaderEx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class JsonReaders {
  private JsonReaders() {
  }

  private static void checkIsNull(JsonReaderEx reader, String fieldName) {
    if (reader.peek() == JsonToken.NULL) {
      throw new RuntimeException("Field is not nullable" + (fieldName == null ? "" : (": " + fieldName)));
    }
  }

  public static String readString(JsonReaderEx reader, String fieldName) {
    checkIsNull(reader, fieldName);
    return reader.nextString();
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

  public static String readNullableString(JsonReaderEx reader) {
    if (reader.peek() == JsonToken.NULL) {
      reader.skipValue();
      return null;
    }
    return reader.nextString();
  }

  public static boolean readBoolean(JsonReaderEx reader, String fieldName) {
    checkIsNull(reader, fieldName);
    return reader.nextBoolean();
  }

  public static boolean readNullableBoolean(JsonReaderEx reader) {
    if (reader.peek() == JsonToken.NULL) {
      reader.skipValue();
      return false;
    }
    return reader.nextBoolean();
  }

  public static int readInt(JsonReaderEx reader, String fieldName) {
    checkIsNull(reader, fieldName);
    return reader.nextInt();
  }

  public static int readNullableInt(JsonReaderEx reader) {
    if (reader.peek() == JsonToken.NULL) {
      reader.skipValue();
      return -1;
    }
    return reader.nextInt();
  }

  public static long readLong(JsonReaderEx reader, String fieldName) {
    checkIsNull(reader, fieldName);
    return reader.nextLong();
  }

  public static double readDouble(JsonReaderEx reader, String fieldName) {
    checkIsNull(reader, fieldName);
    return reader.nextDouble();
  }

  public static long readNullableLong(JsonReaderEx reader) {
    if (reader.peek() == JsonToken.NULL) {
      reader.skipValue();
      return -1;
    }
    return reader.nextLong();
  }

  public static <T extends Enum<T>> T readEnum(JsonReaderEx reader, String fieldName, Class<T> enumClass) {
    checkIsNull(reader, fieldName);
    try {
      return Enum.valueOf(enumClass, readEnumName(reader));
    }
    catch (IllegalArgumentException ignored) {
      return Enum.valueOf(enumClass, "NO_ENUM_CONST");
    }
  }

  public static String convertRawEnumName(@NotNull String enumValue) {
    StringBuilder builder = new StringBuilder(enumValue.length() + 4);
    boolean prevIsLowerCase = false;
    for (int i = 0; i < enumValue.length(); i++) {
      char c = enumValue.charAt(i);
      if (c == '-' || c == ' ') {
        builder.append('_');
        continue;
      }

      if (Character.isUpperCase(c)) {
        // second check handle "CSPViolation" (transform to CSP_VIOLATION)
        if (prevIsLowerCase || ((i + 1) < enumValue.length() && Character.isLowerCase(enumValue.charAt(i + 1)))) {
          builder.append('_');
        }
        builder.append(c);
      }
      else {
        builder.append(Character.toUpperCase(c));
        prevIsLowerCase = true;
      }
    }
    return builder.toString();
  }

  private static String readEnumName(JsonReaderEx reader) {
    return convertRawEnumName(reader.nextString());
  }

  public static <T extends Enum<T>> T readNullableEnum(JsonReaderEx reader, Class<T> enumClass) {
    if (reader.peek() == JsonToken.NULL) {
      reader.skipValue();
      return null;
    }
    return Enum.valueOf(enumClass, readEnumName(reader));
  }

  public static <T> List<T> readObjectArray(JsonReaderEx reader, String fieldName, ObjectFactory<T> factory, boolean nullable) {
    if (reader.peek() == JsonToken.NULL) {
      if (nullable) {
        reader.skipValue();
        return null;
      }
      else {
        checkIsNull(reader, fieldName);
      }
    }

    reader.beginArray();
    if (!reader.hasNext()) {
      reader.endArray();
      if (nullable) {
        return null;
      }
      else {
        return Collections.emptyList();
      }
    }

    List<T> result = new ArrayList<T>();
    do {
      result.add(factory.read(reader));
    }
    while (reader.hasNext());
    reader.endArray();
    return result;
  }

  public static Map<?, ?> readMap(JsonReaderEx reader, String fieldName) {
    checkIsNull(reader, fieldName);
    reader.beginObject();
    if (!reader.hasNext()) {
      reader.endObject();
      return Collections.emptyMap();
    }
    return nextObject(reader);
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
    Map<String, Object> map = new THashMap<String, Object>();
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

    List<T> list = new ArrayList<T>();
    do {
      //noinspection unchecked
      list.add((T)read(reader));
    }
    while (reader.hasNext());
    reader.endArray();
    return list;
  }

  public static List<String> readListOfPrimitive(JsonReaderEx reader) {
    reader.beginArray();
    if (!reader.hasNext()) {
      reader.endArray();
      return Collections.emptyList();
    }

    List<String> list = new ArrayList<String>();
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

    List<StringIntPair> result = new ArrayList<StringIntPair>();
    do {
      reader.beginArray();
      result.add(new StringIntPair(reader.nextInt(), reader.nextString()));
      reader.endArray();
    }
    while (reader.hasNext());
    reader.endArray();
    return result;
  }

  public static JsonReaderEx createReader(CharSequence string) {
    return new JsonReaderEx(string);
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