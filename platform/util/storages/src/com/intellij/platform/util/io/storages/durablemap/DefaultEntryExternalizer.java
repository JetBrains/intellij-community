// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap;

import com.intellij.platform.util.io.storages.DataExternalizerEx;
import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Record format: [keySize:int32][keyBytes][valueBytes]
 * keySize sign-bit is used for marking 'deleted'/value=null records: keySize<0 means record is deleted
 */
public class DefaultEntryExternalizer<K, V> implements EntryExternalizer<K, V> {

  private static final int HEADER_SIZE = Integer.BYTES;

  private final @NotNull KeyDescriptorEx<K> keyDescriptor;
  private final @NotNull DataExternalizerEx<V> valueExternalizer;

  public DefaultEntryExternalizer(@NotNull KeyDescriptorEx<K> keyDescriptor,
                                  @NotNull DataExternalizerEx<V> valueExternalizer) {
    this.keyDescriptor = keyDescriptor;
    this.valueExternalizer = valueExternalizer;
  }

  @Override
  public KnownSizeRecordWriter writerFor(@NotNull K key,
                                         @Nullable V value) throws IOException {
    KnownSizeRecordWriter keyWriter = keyDescriptor.writerFor(key);
    int keySize = keyWriter.recordSize();
    if (keySize < 0) {
      throw new AssertionError("keySize(" + key + ")=" + keySize + ": must be strictly positive");
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
    int header = readHeader(input);
    int keyRecordSize = keySize(header);
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
    int header = readHeader(input);
    int keyRecordSize = keySize(header);
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

  private static int readHeader(@NotNull ByteBuffer keyBuffer) {
    return keyBuffer.getInt(0);
  }

  private static void putHeader(@NotNull ByteBuffer keyBuffer,
                                int keySize,
                                boolean valueEmpty) {
    if (keySize < 0) {
      throw new IllegalArgumentException("keySize(=" + keySize + ") must have highest bit 0");
    }
    if (valueEmpty) {
      int highestBitMask = 0b1000_0000_0000_0000;
      keyBuffer.putInt(0, keySize | highestBitMask);
    }
    else {
      //MAYBE RC: use varint DataInputOutputUtil.writeINT(buffer, keySize)?
      //          -- but this makes record size computation more difficult
      keyBuffer.putInt(0, keySize);
    }
  }

  private static int keySize(int header) {
    int highestBitMask = 0b1000_0000_0000_0000;
    return header & ~highestBitMask;
  }

  /** @return value is void -- null/deleted (we don't differentiate those two cases in this map impl) */
  private static boolean isValueVoid(int header) {
    int highestBitMask = 0b1000_0000_0000_0000;
    return (header & highestBitMask) != 0;
  }

  private record NullValueEntryWriter(@NotNull KnownSizeRecordWriter keyWriter,
                                      int keySize) implements KnownSizeRecordWriter {

    NullValueEntryWriter(@NotNull KnownSizeRecordWriter keyWriter) {
      this(keyWriter, keyWriter.recordSize());
    }

    @Override
    public ByteBuffer write(@NotNull ByteBuffer buffer) throws IOException {
      putHeader(buffer, keySize, /* deleted: */ true);
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
      putHeader(buffer, keySize, /* deleted: */ false);
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
