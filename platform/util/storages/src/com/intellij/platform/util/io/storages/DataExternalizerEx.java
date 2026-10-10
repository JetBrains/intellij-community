// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.io.DataEnumerator;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import com.intellij.util.io.UnsyncByteArrayOutputStream;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.Objects;

/**
 * Analog of {@link DataExternalizer}, but with {@link MemorySegment} instead of
 * {@link java.io.InputStream} and {@link java.io.OutputStream}
 * The input of {@link #read(MemorySegment)} is the exact record slice. Its size must not exceed {@link Integer#MAX_VALUE}.
 */
@ApiStatus.Internal
public interface DataExternalizerEx<T> {

  T read(@NotNull MemorySegment input) throws IOException;

  KnownSizeRecordWriter writerFor(@NotNull T value) throws IOException;

  /** @return true if all the values are serialized into the same number of bytes */
  default boolean isRecordSizeConstant() {
    return recordSizeIfConstant() >= 0;
  }

  /**
   * if positive => all the values are serialized into the same number of bytes.
   * Apt {@link KnownSizeRecordWriter} must always return the same record size, regardless of the value
   */
  default int recordSizeIfConstant() {
    return -1;
  }

  /**
   * Adapts old-school {@link KeyDescriptor} to new {@link KeyDescriptorEx}.
   * <p>
   * <p/>
   * Implementation is not 100% optimal -- it does unnecessary allocations and copying -- but usually good enough to
   * start using new API (i.e. {@link DataEnumerator}), and see does it make any difference.
   * <p/>
   * Still, one could do better by using more 'idiomatic' API -- but it takes more effort.
   */
  static <K> @NotNull DataExternalizerEx<K> adapt(@NotNull DataExternalizer<K> oldSchoolDescriptor) {
    if (oldSchoolDescriptor instanceof KeyDescriptor<K> descriptor) {
      return KeyDescriptorEx.adapt(descriptor);
    }

    //Do not wrap, if oldSchoolDescriptor already implements new interface
    // -> allows for 'bilingual' implementation (that implements both old&new
    // ifaces) to work efficiently
    if (oldSchoolDescriptor instanceof DataExternalizerEx<?> exExternalizer) {
      //noinspection unchecked
      return (DataExternalizerEx<K>)exExternalizer;
    }

    return new StreamDataExternalizerExAdapter<>(oldSchoolDescriptor);
  }

  /// Writes one record to a caller-owned segment.
  interface KnownSizeRecordWriter {
    /// Writes the record at the specified offset.
    /// The implementation must validate the complete target range before it changes the segment.
    void write(@NotNull MemorySegment target, long startingOffset) throws IOException;

    /// @return the non-negative number of bytes written by [#write(MemorySegment, long)]
    int recordSize();

    KnownSizeRecordWriter NOTHING = new KnownSizeRecordWriter() {
      @Override
      public void write(@NotNull MemorySegment target, long startingOffset) {
        Objects.checkFromIndexSize(startingOffset, 0, target.byteSize());
      }

      @Override
      public int recordSize() {
        return 0;
      }
    };
  }

  static @NotNull KnownSizeRecordWriter fromBytes(byte @NotNull [] bytes) {
    return new ByteArrayRecordWriter(bytes, 0, bytes.length);
  }

  static @NotNull KnownSizeRecordWriter fromBytes(@NotNull ByteArraySequence bytes) {
    return new ByteArrayRecordWriter(bytes.getInternalBuffer(), bytes.getOffset(), bytes.getLength());
  }

  static @NotNull KnownSizeRecordWriter fromBytes(byte @NotNull [] bytes, int offset, int length) {
    return new ByteArrayRecordWriter(bytes, offset, length);
  }
}

/// Adapts stream serialization once for data and key descriptors.
final class StreamDataExternalizerExAdapter<T> implements DataExternalizerEx<T> {
  private final DataExternalizer<T> externalizer;

  StreamDataExternalizerExAdapter(@NotNull DataExternalizer<T> externalizer) {
    this.externalizer = externalizer;
  }

  @Override
  public T read(@NotNull MemorySegment input) throws IOException {
    if (input.byteSize() > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("The record is too large: " + input.byteSize());
    }
    // Serialization/deserialization just bridges between MemorySegment and ByteArrayInput/OutputStream

    //MAYBE RC: one allocation & one copy could be removed by implementing something like MemorySegmentBackedInputStream
    //          instead of UnsyncByteArrayInputStream
    byte[] content = input.toArray(ValueLayout.JAVA_BYTE);
    return externalizer.read(new DataInputStream(new UnsyncByteArrayInputStream(content)));
  }

  @Override
  public KnownSizeRecordWriter writerFor(@NotNull T value) throws IOException {
    var stream = new UnsyncByteArrayOutputStream(64);
    try (var output = new DataOutputStream(stream)) {
      externalizer.save(output, value);
    }
    return DataExternalizerEx.fromBytes(stream.toByteArraySequence());
  }

  @Override
  public String toString() {
    return "DataExternalizerExAdapter[adapted: " + externalizer + "]";
  }
}

/** Simplest implementation: writer over the record already serialized into a byte[] */
final class ByteArrayRecordWriter implements DataExternalizerEx.KnownSizeRecordWriter {
  private final byte[] bytes;
  private final int sourceOffset;
  private final int length;

  ByteArrayRecordWriter(byte @NotNull [] bytes, int sourceOffset, int length) {
    Objects.checkFromIndexSize(sourceOffset, length, bytes.length);
    this.bytes = bytes;
    this.sourceOffset = sourceOffset;
    this.length = length;
  }

  @Override
  public void write(@NotNull MemorySegment target, long startingOffset) {
    Objects.checkFromIndexSize(startingOffset, length, target.byteSize());
    MemorySegment.copy(bytes, sourceOffset, target, ValueLayout.JAVA_BYTE, startingOffset, length);
  }

  @Override
  public int recordSize() {
    return length;
  }

  @Override
  public String toString() {
    return "ByteArrayRecordWriter[" + IOUtil.toHexString(Arrays.copyOfRange(bytes, sourceOffset, sourceOffset + length)) + "]";
  }
}
