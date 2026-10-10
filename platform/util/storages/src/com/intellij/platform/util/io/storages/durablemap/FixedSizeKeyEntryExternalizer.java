// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap;

import com.intellij.platform.util.io.storages.DataExternalizerEx;
import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

/**
 * Record format: [header:int8][keyBytes][valueBytes]
 * header is used for marking 'deleted'/value=null records: header=0 means record is deleted
 */
public class FixedSizeKeyEntryExternalizer<K, V> implements EntryExternalizer<K, V> {
  private static final int HEADER_SIZE = 1;

  private final @NotNull KeyDescriptorEx<K> keyDescriptor;
  private final @NotNull DataExternalizerEx<V> valueExternalizer;

  private final int keyRecordSize;

  public FixedSizeKeyEntryExternalizer(@NotNull KeyDescriptorEx<K> keyDescriptor,
                                       @NotNull DataExternalizerEx<V> valueExternalizer) {
    if (!keyDescriptor.isRecordSizeConstant()) {
      throw new IllegalArgumentException("Keys must be fixed-size");
    }
    keyRecordSize = keyDescriptor.recordSizeIfConstant();
    this.keyDescriptor = keyDescriptor;
    this.valueExternalizer = valueExternalizer;
  }

  @Override
  public KnownSizeRecordWriter writerFor(@NotNull K key, @Nullable V value) throws IOException {
    var keyWriter = keyDescriptor.writerFor(key);
    checkKeySize(keyWriter);
    if (value == null) {
      return new NullValueEntryWriter(keyWriter);
    }
    return new NonNullValueEntryWriter(keyWriter, valueExternalizer.writerFor(value));
  }

  @Override
  public @NotNull Entry<K, V> read(@NotNull MemorySegment input) throws IOException {
    byte header = readHeader(input);
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
    checkKeySize(keyWriter);
    return new NonNullValueEntryWriter(keyWriter, /* valueWriter: */ KnownSizeRecordWriter.NOTHING);
  }

  @Override
  public @Nullable Entry<K, V> readIfKeyMatch(@NotNull MemorySegment input, @NotNull K expectedKey) throws IOException {
    byte header = readHeader(input);
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
    if (isValueVoid(readHeader(input))) {
      return null;
    }
    return keyDescriptor.read(input.asSlice(HEADER_SIZE, keyRecordSize));
  }

  private void checkKeySize(@NotNull KnownSizeRecordWriter keyWriter) {
    if (keyWriter.recordSize() != keyRecordSize) {
      throw new AssertionError("fixedRecordSize(=" + keyRecordSize + ") != keyWriter.recordSize(=" + keyWriter.recordSize() + ")");
    }
  }

  private static byte readHeader(@NotNull MemorySegment input) {
    return input.get(JAVA_BYTE, 0);
  }

  private static void putHeader(@NotNull MemorySegment target, boolean valueIsNull) {
    target.set(JAVA_BYTE, 0, (byte)(valueIsNull ? 0 : 1));
  }

  /** @return value is void -- null/deleted (we don't differentiate those two cases in this map impl) */
  private static boolean isValueVoid(byte header) {
    return header == 0;
  }

  private record NullValueEntryWriter(@NotNull KnownSizeRecordWriter keyWriter,
                                      int keySize) implements KnownSizeRecordWriter {
    NullValueEntryWriter(@NotNull KnownSizeRecordWriter keyWriter) {
      this(keyWriter, keyWriter.recordSize());
    }

    @Override
    public void write(@NotNull MemorySegment target, long startingOffset) throws IOException {
      var record = target.asSlice(startingOffset, recordSize());
      putHeader(record, /* deleted: */ true);
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
      this.keySize = keyWriter.recordSize();
      this.valueSize = valueWriter.recordSize();
    }

    @Override
    public void write(@NotNull MemorySegment target, long startingOffset) throws IOException {
      var record = target.asSlice(startingOffset, recordSize());
      putHeader(record, /* deleted: */ false);
      keyWriter.write(record, HEADER_SIZE);
      valueWriter.write(record, HEADER_SIZE + keySize);
    }

    @Override
    public int recordSize() {
      return HEADER_SIZE + keySize + valueSize;
    }
  }
}
