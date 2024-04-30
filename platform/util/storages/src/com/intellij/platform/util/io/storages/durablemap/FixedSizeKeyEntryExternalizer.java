// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap;

import com.intellij.platform.util.io.storages.DataExternalizerEx;
import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;

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
    this.keyRecordSize = keyDescriptor.recordSizeIfConstant();
    this.keyDescriptor = keyDescriptor;
    this.valueExternalizer = valueExternalizer;
  }

  @Override
  public KnownSizeRecordWriter writerFor(@NotNull K key,
                                         @Nullable V value) throws IOException {
    KnownSizeRecordWriter keyWriter = keyDescriptor.writerFor(key);
    if (keyWriter.recordSize() != keyRecordSize) {
      throw new AssertionError("fixedRecordSize(=" + keyRecordSize + ") != keyWriter.recordSize(=" + keyWriter.recordSize() + ")");
    }
    if (value == null) {
      return new NullValueEntryWriter(keyWriter);
    }
    else {
      KnownSizeRecordWriter valueWriter = valueExternalizer.writerFor(value);
      return new NonNullValueEntryWriter(keyWriter, valueWriter);
    }
  }

  @Override
  public @NotNull Entry<K, V> read(@NotNull ByteBuffer input) throws IOException {
    byte header = readHeader(input);
    boolean valueIsNull = isValueVoid(header);

    int recordSize = input.remaining();

    ByteBuffer keyRecordSlice = input
      .position(HEADER_SIZE)
      .limit(HEADER_SIZE + keyRecordSize);
    K key = keyDescriptor.read(keyRecordSlice);

    if (valueIsNull) {
      return new Entry<>(key, null);
    }

    ByteBuffer valueRecordSlice = input
      .position(HEADER_SIZE + keyRecordSize)
      .limit(recordSize);
    V candidateValue = valueExternalizer.read(valueRecordSlice);
    return new Entry<>(key, candidateValue);
  }

  @Override
  public @Nullable Entry<K, V> readIfKeyMatch(@NotNull ByteBuffer input,
                                              @NotNull K expectedKey) throws IOException {
    byte header = readHeader(input);
    boolean valueIsNull = isValueVoid(header);

    int recordSize = input.remaining();

    ByteBuffer keyRecordSlice = input
      .position(HEADER_SIZE)
      .limit(HEADER_SIZE + keyRecordSize);
    K candidateKey = keyDescriptor.read(keyRecordSlice);
    if (!keyDescriptor.isEqual(expectedKey, candidateKey)) {
      return null;
    }

    if (valueIsNull) {
      return new Entry<>(expectedKey, null);
    }

    ByteBuffer valueRecordSlice = input
      .position(HEADER_SIZE + keyRecordSize)
      .limit(recordSize);
    V candidateValue = valueExternalizer.read(valueRecordSlice);
    return new Entry<>(expectedKey, candidateValue);
  }


  private static byte readHeader(@NotNull ByteBuffer keyBuffer) {
    return keyBuffer.get(0);
  }

  private static void putHeader(@NotNull ByteBuffer keyBuffer,
                                boolean valueEmpty) {
    byte header = (byte)(valueEmpty ? 0 : 1);
    keyBuffer.put(0, header);
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
    public ByteBuffer write(@NotNull ByteBuffer buffer) throws IOException {
      putHeader(buffer, /* deleted: */ true);
      keyWriter.write(
        buffer.position(HEADER_SIZE).limit(HEADER_SIZE + keySize)
      );
      return buffer;
    }

    @Override
    public int recordSize() {
      return HEADER_SIZE + keySize;
    }
  }

  private static class NonNullValueEntryWriter implements KnownSizeRecordWriter {
    private final KnownSizeRecordWriter keyWriter;
    private final KnownSizeRecordWriter valueWriter;
    private final int keySize;
    private final int valueSize;

    NonNullValueEntryWriter(@NotNull KnownSizeRecordWriter keyWriter,
                            @NotNull KnownSizeRecordWriter valueWriter) {
      this.keyWriter = keyWriter;
      this.valueWriter = valueWriter;
      this.keySize = keyWriter.recordSize();
      this.valueSize = valueWriter.recordSize();
    }

    @Override
    public ByteBuffer write(@NotNull ByteBuffer buffer) throws IOException {
      putHeader(buffer, /* deleted: */ false);
      keyWriter.write(buffer.position(HEADER_SIZE).limit(HEADER_SIZE + keySize));
      valueWriter.write(buffer.position(HEADER_SIZE + keySize).limit(HEADER_SIZE + keySize + valueSize));
      return buffer;
    }

    @Override
    public int recordSize() {
      return HEADER_SIZE + keySize + valueSize;
    }
  }
}
