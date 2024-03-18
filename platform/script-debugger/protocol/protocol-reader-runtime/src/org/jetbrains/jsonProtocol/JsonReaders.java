// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jsonProtocol;

import com.google.gson.stream.JsonToken;
import com.intellij.util.ArrayUtilRt;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.JsonReaderEx;

import java.util.*;

public final class JsonReaders {
  public static final ObjectFactory<String> STRING_OBJECT_FACTORY = new ObjectFactory<>() {
    @Override
    public String read(JsonReaderEx reader) {
      return reader.nextString();
    }
  };

  public static <T extends Enum<T>> List<T> readEnumArray(@NotNull JsonReaderEx reader, @NotNull Class<T> enumClass) {
    return readObjectArray(reader, new EnumFactory<>(enumClass));
  }

  public static double[] readDoubleArray(JsonReaderEx reader) {
    checkIsNull(reader);
    reader.beginArray();
    if (!reader.hasNext()) {
      reader.endArray();
      return new double[0];
    }

    DoubleArrayList result = new DoubleArrayList();
    do {
      result.add(reader.nextDouble());
    }
    while (reader.hasNext());
    reader.endArray();
    return result.toDoubleArray();
  }

  private JsonReaders() {
  }

  public static <T> ObjectFactory<Map<String, T>> mapFactory(@NotNull ObjectFactory<? extends T> valueFactory) {
    return new MapFactory<>(valueFactory);
  }

  private static void checkIsNull(JsonReaderEx reader) {
    if (reader.peek() == JsonToken.NULL) {
      throw new RuntimeException("Field is not nullable");
    }
  }

  public static boolean readsNull(JsonReaderEx reader) {
    if (reader.peek() == JsonToken.NULL) {
      reader.skipValue();
      return true;
    }
    else return false;
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

  public static final class EnumFactory<T extends Enum<T>> extends ObjectFactory<T> {
    private final Class<T> enumClass;

    public EnumFactory(Class<T> enumClass) {
      this.enumClass = enumClass;
    }

    @Override
    public T read(JsonReaderEx reader) {
      return readEnum(reader, enumClass);
    }
  }

  public static <T> List<T> readObjectArray(@NotNull JsonReaderEx reader, @NotNull ObjectFactory<? extends T> factory) {
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

  public static <T> Map<String, T> readMap(@NotNull JsonReaderEx reader, @Nullable ObjectFactory<? extends T> factory) {
    if (reader.peek() == JsonToken.NULL) {
      reader.skipValue();
      return null;
    }

    reader.beginObject();
    if (!reader.hasNext()) {
      reader.endObject();
      return Collections.emptyMap();
    }

    Map<String, T> map = new HashMap<>();
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
    return switch (reader.peek()) {
      case BEGIN_ARRAY -> nextList(reader);
      case BEGIN_OBJECT -> {
        reader.beginObject();
        yield nextObject(reader);
      }
      case STRING -> reader.nextString();
      case NUMBER -> reader.nextDouble();
      case BOOLEAN -> reader.nextBoolean();
      case NULL -> {
        reader.nextNull();
        yield null;
      }
      default -> throw new IllegalStateException();
    };
  }

  public static Map<String, Object> nextObject(JsonReaderEx reader) {
    Map<String, Object> map = new HashMap<>();
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
      list.add(reader.nextString(true));
    }
    while (reader.hasNext());
    reader.endArray();
    return list;
  }

  public static long[] readLongArray(JsonReaderEx reader) {
    checkIsNull(reader);
    reader.beginArray();
    if (!reader.hasNext()) {
      reader.endArray();
      return ArrayUtilRt.EMPTY_LONG_ARRAY;
    }

    LongArrayList result = new LongArrayList();
    do {
      result.add(reader.nextLong());
    }
    while (reader.hasNext());
    reader.endArray();
    return result.toLongArray();
  }

  public static final class WrapperFactory<T> extends ObjectFactory<T> {
    private final Function1<? super JsonReaderEx, ? extends T> innerReader;

    public WrapperFactory(Function1<? super JsonReaderEx, ? extends T> reader) { innerReader = reader; }

    @Override
    public T read(JsonReaderEx reader) {
      return innerReader.invoke(reader);
    }
  }

  public static int[] readIntArray(JsonReaderEx reader) {
    checkIsNull(reader);
    reader.beginArray();
    if (!reader.hasNext()) {
      reader.endArray();
      return ArrayUtilRt.EMPTY_INT_ARRAY;
    }

    IntList result = new IntArrayList();
    do {
      result.add(reader.nextInt());
    }
    while (reader.hasNext());
    reader.endArray();
    return result.toIntArray();
  }

  public static List<StringIntPair> readIntStringPairs(JsonReaderEx reader) {
    checkIsNull(reader);
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