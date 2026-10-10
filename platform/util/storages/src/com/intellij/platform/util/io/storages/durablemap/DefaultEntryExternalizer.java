// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap;

import com.intellij.platform.util.io.storages.DataExternalizerEx;
import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

import static com.intellij.platform.util.io.storages.PersistentFormatLayouts.JAVA_INT_UNALIGNED;

/**
 * Record format: [keySize:int32][keyBytes][valueBytes]
 * keySize sign-bit is used for marking 'deleted'/value=null records: keySize<0 means record is deleted
 */
public class DefaultEntryExternalizer<K, V> implements EntryExternalizer<K, V> {
  private static final int HEADER_SIZE = Integer.BYTES;
  private static final int VALUE_IS_NULL_MASK = 0b1000_0000_0000_0000;

  private final @NotNull KeyDescriptorEx<K> keyDescriptor;
  private final @NotNull DataExternalizerEx<V> valueExternalizer;

  public DefaultEntryExternalizer(@NotNull KeyDescriptorEx<K> keyDescriptor,
                                  @NotNull DataExternalizerEx<V> valueExternalizer) {
    this.keyDescriptor = keyDescriptor;
    this.valueExternalizer = valueExternalizer;
  }

  @Override
  public KnownSizeRecordWriter writerFor(@NotNull K key, @Nullable V value) throws IOException {
    var keyWriter = keyDescriptor.writerFor(key);
    checkKeySize(key, keyWriter.recordSize());
    if (value == null) {
      return new NullValueEntryWriter(keyWriter);
    }
    return new NonNullValueEntryWriter(keyWriter, valueExternalizer.writerFor(value));
  }

  @Override
  public @NotNull Entry<K, V> read(@NotNull MemorySegment input) throws IOException {
    int header = readHeader(input);
    int keyRecordSize = keySize(header);
    K key = keyDescriptor.read(input.asSlice(HEADER_SIZE, keyRecordSize));
    if (isValueVoid(header)) {
      return new Entry<>(key, null);
    }

    long valueOffset = HEADER_SIZE + keyRecordSize;
    V value = valueExternalizer.read(input.asSlice(valueOffset));
    return new Entry<>(key, value);
  }

  @Override
  public KnownSizeRecordWriter writerForEntryHeader(@NotNull K key) throws IOException {
    var keyWriter = keyDescriptor.writerFor(key);
    checkKeySize(key, keyWriter.recordSize());
    return new NonNullValueEntryWriter(keyWriter, /*valueWriter: */ KnownSizeRecordWriter.NOTHING);
  }

  @Override
  public @Nullable Entry<K, V> readIfKeyMatch(@NotNull MemorySegment input, @NotNull K expectedKey) throws IOException {
    int header = readHeader(input);
    int keyRecordSize = keySize(header);
    K candidateKey = keyDescriptor.read(input.asSlice(HEADER_SIZE, keyRecordSize));
    if (!keyDescriptor.isEqual(expectedKey, candidateKey)) {
      return null;
    }
    if (isValueVoid(header)) {
      return new Entry<>(expectedKey, null);
    }

    long valueOffset = HEADER_SIZE + keyRecordSize;
    V value = valueExternalizer.read(input.asSlice(valueOffset));
    return new Entry<>(expectedKey, value);
  }

  @Override
  public @Nullable K readKey(@NotNull MemorySegment input) throws IOException {
    int header = readHeader(input);
    if (isValueVoid(header)) {
      return null;
    }
    int keyRecordSize = keySize(header);
    return keyDescriptor.read(input.asSlice(HEADER_SIZE, keyRecordSize));
  }

  private static int readHeader(@NotNull MemorySegment input) {
    return input.get(JAVA_INT_UNALIGNED, 0);
  }

  private static void putHeader(@NotNull MemorySegment target, int keySize, boolean valueIsNull) {
    if (keySize < 0 || (keySize & VALUE_IS_NULL_MASK) != 0) {
      throw new IllegalArgumentException("keySize(=" + keySize + ") must not use the null marker bit");
    }
    //MAYBE RC: use varint DataInputOutputUtil.writeINT(buffer, keySize)?
    //          -- but this makes record size computation more difficult
    target.set(JAVA_INT_UNALIGNED, 0, valueIsNull ? keySize | VALUE_IS_NULL_MASK : keySize);
  }

  private static int keySize(int header) {
    return header & ~VALUE_IS_NULL_MASK;
  }

  /** @return value is void -- null/deleted (we don't differentiate those two cases in this map impl) */
  private static boolean isValueVoid(int header) {
    return (header & VALUE_IS_NULL_MASK) != 0;
  }

  private static void checkKeySize(@NotNull Object key, int keySize) {
    if (keySize < 0 || (keySize & VALUE_IS_NULL_MASK) != 0) {
      throw new AssertionError("keySize(" + key + ")=" + keySize + ": must not use the null marker bit");
    }
  }

  private record NullValueEntryWriter(@NotNull KnownSizeRecordWriter keyWriter,
                                      int keySize) implements KnownSizeRecordWriter {
    NullValueEntryWriter(@NotNull KnownSizeRecordWriter keyWriter) {
      this(keyWriter, keyWriter.recordSize());
    }

    @Override
    public void write(@NotNull MemorySegment target, long startingOffset) throws IOException {
      var record = target.asSlice(startingOffset, recordSize());
      putHeader(record, keySize, /* deleted: */ true);
      keyWriter.write(record, HEADER_SIZE);
    }

    @Override
    public int recordSize() {
      return HEADER_SIZE + keySize;
    }
  }

  private static final class NonNullValueEntryWriter implements KnownSizeRecordWriter {
    private final KnownSizeRecordWriter keyWriter;
    private final KnownSizeRecordWriter valueWriter;
    private final int keySize;
    private final int valueSize;

    private NonNullValueEntryWriter(@NotNull KnownSizeRecordWriter keyWriter,
                                    @NotNull KnownSizeRecordWriter valueWriter) {
      this.keyWriter = keyWriter;
      this.valueWriter = valueWriter;
      keySize = keyWriter.recordSize();
      valueSize = valueWriter.recordSize();
    }

    @Override
    public void write(@NotNull MemorySegment target, long startingOffset) throws IOException {
      var record = target.asSlice(startingOffset, recordSize());
      putHeader(record, keySize, /* deleted: */ false);
      keyWriter.write(record, HEADER_SIZE);
      valueWriter.write(record, HEADER_SIZE + keySize);
    }

    @Override
    public int recordSize() {
      return HEADER_SIZE + keySize + valueSize;
    }
  }
}
