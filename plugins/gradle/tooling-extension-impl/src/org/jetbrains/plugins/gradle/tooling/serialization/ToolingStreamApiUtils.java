// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonType;
import com.amazon.ion.IonWriter;
import com.amazon.ion.system.IonBinaryWriterBuilder;
import com.intellij.util.ThrowableConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.Supplier;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

public final class ToolingStreamApiUtils {

  public static final String OBJECT_ID_FIELD = "objectID";
  public static final String MAP_KEY_FIELD = "key";
  public static final String MAP_VALUE_FIELD = "value";

  // todo what about PooledBlockAllocatorProvider?
  public static @NotNull IonBinaryWriterBuilder createIonWriter() {
    return IonBinaryWriterBuilder.standard()
      .withLocalSymbolTableAppendEnabled()
      .withStreamCopyOptimized(true);
  }

  public static void writeInt(
    @NotNull IonWriter writer,
    @NotNull String fieldName,
    int value
  ) throws IOException {
    writer.setFieldName(fieldName);
    writer.writeInt(value);
  }

  public static int readInt(
    @NotNull IonReader reader,
    @Nullable String fieldName
  ) {
    reader.next();
    assertFieldName(reader, fieldName);
    return reader.intValue();
  }

  public static void writeInteger(
    @NotNull IonWriter writer,
    @NotNull String fieldName,
    @Nullable Integer value
  ) throws IOException {
    writer.setFieldName(fieldName);
    if (value == null) {
      writer.writeNull(IonType.INT);
    } else {
      writer.writeInt(value);
    }
  }

  public static @Nullable Integer readInteger(
    @NotNull IonReader reader,
    @Nullable String fieldName
  ) {
    reader.next();
    assertFieldName(reader, fieldName);
    if (reader.isNullValue()) {
      return null;
    } else {
      return reader.intValue();
    }
  }

  public static void writeLong(
    @NotNull IonWriter writer,
    @NotNull String fieldName,
    long value
  ) throws IOException {
    writer.setFieldName(fieldName);
    writer.writeInt(value);
  }

  public static long readLong(
    @NotNull IonReader reader,
    @Nullable String fieldName
  ) {
    reader.next();
    assertFieldName(reader, fieldName);
    return reader.longValue();
  }

  public static void writeBoolean(
    @NotNull IonWriter writer,
    @NotNull String fieldName,
    boolean value
  ) throws IOException {
    writer.setFieldName(fieldName);
    writer.writeBool(value);
  }

  public static boolean readBoolean(
    @NotNull IonReader reader,
    @Nullable String fieldName
  ) {
    reader.next();
    assertFieldName(reader, fieldName);
    return reader.booleanValue();
  }

  public static void writeString(
    @NotNull IonWriter writer,
    @NotNull String fieldName,
    @Nullable String value
  ) throws IOException {
    writer.setFieldName(fieldName);
    writer.writeString(value);
  }

  public static @Nullable String readString(
    @NotNull IonReader reader,
    @Nullable String fieldName
  ) {
    IonType type = reader.next();
    assertFieldName(reader, fieldName);
    if (type == null) return null;
    return reader.stringValue();
  }

  public static @Nullable String readString(
    @NotNull IonReader reader,
    @Nullable String fieldName,
    @NotNull ConcurrentMap<String, String> stringCache
  ) {
    String str = readString(reader, fieldName);
    if (str == null) {
      return null;
    }
    String old = stringCache.putIfAbsent(str, str);
    if (old != null) {
      return old;
    }
    return str;
  }

  public static void writeFile(
    @NotNull IonWriter writer,
    @NotNull String fieldName,
    @Nullable File file
  ) throws IOException {
    writeString(writer, fieldName, file == null ? null : file.getPath());
  }

  public static @Nullable File readFile(
    @NotNull IonReader reader,
    @Nullable String fieldName
  ) {
    String filePath = readString(reader, fieldName);
    return filePath == null ? null : new File(filePath);
  }

  public static <E> void writeCollection(
    @NotNull IonWriter writer,
    @NotNull String fieldName,
    @NotNull Collection<E> collection,
    @NotNull ThrowableConsumer<? super E, ? extends IOException> elementWriter
  ) throws IOException {
    writer.setFieldName(fieldName);
    writer.stepIn(IonType.LIST);
    for (E element : collection) {
      elementWriter.consume(element);
    }
    writer.stepOut();
  }

  public static <E, C extends Collection<E>> C readCollection(
    @NotNull IonReader reader,
    @Nullable String fieldName,
    @NotNull Supplier<? extends C> newCollection,
    @NotNull Supplier<? extends @Nullable E> elementReader
  ) {
    reader.next();
    assertFieldName(reader, fieldName);
    reader.stepIn();
    C collection = newCollection.get();
    E element;
    while ((element = elementReader.get()) != null) {
      collection.add(element);
    }
    reader.stepOut();
    return collection;
  }

  public static <E> List<E> readList(
    @NotNull IonReader reader,
    @Nullable String fieldName,
    @NotNull Supplier<? extends @Nullable E> elementReader
  ) {
    return readCollection(reader, fieldName, ArrayList::new, elementReader);
  }

  public static <E> Set<E> readSet(
    @NotNull IonReader reader,
    @Nullable String fieldName,
    @NotNull Supplier<? extends @Nullable E> elementReader
  ) {
    return readCollection(reader, fieldName, LinkedHashSet::new, elementReader);
  }

  public static <K, V> void writeMap(
    @NotNull IonWriter writer,
    @NotNull String fieldName,
    @NotNull Map<K, V> map,
    @NotNull ThrowableConsumer<? super K, ? extends IOException> keyWriter,
    @NotNull ThrowableConsumer<? super V, ? extends IOException> valueWriter
  ) throws IOException {
    writer.setFieldName(fieldName);
    writer.stepIn(IonType.LIST);
    for (Map.Entry<K, V> entry : map.entrySet()) {
      writer.stepIn(IonType.STRUCT);
      writer.setFieldName(MAP_KEY_FIELD);
      keyWriter.consume(entry.getKey());
      writer.setFieldName(MAP_VALUE_FIELD);
      valueWriter.consume(entry.getValue());
      writer.stepOut();
    }
    writer.stepOut();
  }

  public static <K, V> @NotNull Map<K, V> readMap(
    @NotNull IonReader reader,
    @Nullable String fieldName,
    @NotNull Supplier<? extends K> keyReader,
    @NotNull Supplier<? extends V> valueReader
  ) {
    reader.next();
    assertFieldName(reader, fieldName);
    reader.stepIn();
    Map<K, V> map = new HashMap<>();
    while (reader.next() != null) {
      reader.stepIn();
      map.put(keyReader.get(), valueReader.get());
      reader.stepOut();
    }
    reader.stepOut();
    return map;
  }

  public static void writeStrings(
    @NotNull IonWriter writer,
    @NotNull String fieldName,
    @NotNull Collection<String> strings
  ) throws IOException {
    writeCollection(writer, fieldName, strings, it ->
      writer.writeString(it)
    );
  }

  public static @NotNull List<String> readStringList(
    @NotNull IonReader reader,
    @Nullable String fieldName
  ) {
    return readList(reader, fieldName, () ->
      readString(reader, null)
    );
  }

  public static @NotNull Set<String> readStringSet(
    @NotNull IonReader reader,
    @Nullable String fieldName
  ) {
    return readSet(reader, fieldName, () ->
      readString(reader, null)
    );
  }

  public static void writeFiles(
    @NotNull IonWriter writer,
    @NotNull String fieldName,
    @NotNull Collection<File> files
  ) throws IOException {
    writeCollection(writer, fieldName, files, it ->
      writer.writeString(it.getPath())
    );
  }

  public static @NotNull List<File> readFileList(
    @NotNull IonReader reader,
    @Nullable String fieldName
  ) {
    return readList(reader, fieldName, () ->
      readFile(reader, null)
    );
  }

  public static @NotNull Set<File> readFileSet(
    @NotNull IonReader reader,
    @Nullable String fieldName
  ) {
    return readSet(reader, fieldName, () ->
      readFile(reader, null)
    );
  }

  public static void assertFieldName(
    @NotNull IonReader reader,
    @Nullable String fieldName
  ) {
    String readerFieldName = reader.getFieldName();
    assert fieldName == null || fieldName.equals(readerFieldName) :
      "Expected field name '" + fieldName + "', got `" + readerFieldName + "' ";
  }

  public static <T> @NotNull T assertNotNull(@Nullable T t) {
    assert t != null;
    return t;
  }
}
