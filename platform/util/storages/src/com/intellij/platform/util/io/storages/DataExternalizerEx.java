// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.SystemProperties;
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
import java.io.InputStream;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.Objects;

import static com.intellij.util.SystemProperties.getBooleanProperty;

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
   * Adapts old-school {@link DataExternalizer} to new {@link DataExternalizerEx}.
   * If an legacyDescriptor also implements {@link DataExternalizerEx} ('bilingual') -- it is returned as-is.
   * Otherwise, an adaptor is used: it reserves hash and equality rules, but serialization format is not guaranteed
   * to be binary-compatible with legacy one, and it is not guaranteed to be 100% optimal performance-wise.
   */
  static <K> @NotNull DataExternalizerEx<K> adapt(@NotNull DataExternalizer<K> legacyDescriptor) {
    if (legacyDescriptor instanceof KeyDescriptor<K> descriptor) {
      return KeyDescriptorEx.adapt(descriptor);
    }

    //Do not wrap, if legacyDescriptor already implements new interface -> allows for 'bilingual' implementation
    // (that implements both old&new ifaces) to work efficiently
    if (legacyDescriptor instanceof DataExternalizerEx<?> exExternalizer) {
      //noinspection unchecked
      return (DataExternalizerEx<K>)exExternalizer;
    }

    return new StreamDataExternalizerExAdapter<>(legacyDescriptor);
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
      public void write(@NotNull MemorySegment target, long startingOffset) { }

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
  static final String DIRECT_READ_PROPERTY = "idea.storages.stream-externalizer-direct-read";
  static final boolean DEFAULT_DIRECT_READ = getBooleanProperty(DIRECT_READ_PROPERTY, true);

  private final DataExternalizer<T> externalizer;
  /// aka 'zero-copy'
  private final boolean readDirectlyViaMemorySegment;

  StreamDataExternalizerExAdapter(@NotNull DataExternalizer<T> externalizer) {
    this(externalizer, DEFAULT_DIRECT_READ);
  }

  StreamDataExternalizerExAdapter(@NotNull DataExternalizer<T> externalizer,
                                  boolean readDirectlyViaMemorySegment) {
    this.externalizer = externalizer;
    this.readDirectlyViaMemorySegment = readDirectlyViaMemorySegment;
  }

  @Override
  public T read(@NotNull MemorySegment input) throws IOException {
    InputStream stream;
    if (readDirectlyViaMemorySegment) {
      stream = new InputStreamOverMemorySegment(input);
    }
    else {
      byte[] content = input.toArray(ValueLayout.JAVA_BYTE);
      stream = new UnsyncByteArrayInputStream(content);
    }
    return externalizer.read(new DataInputStream(stream));
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

  private static final class InputStreamOverMemorySegment extends InputStream {
    private final MemorySegment segment;
    private final int limit;

    private int position;
    private int mark;

    private InputStreamOverMemorySegment(@NotNull MemorySegment segment) {
      this.segment = segment;
      limit = Math.toIntExact(segment.byteSize());
    }

    @Override
    public int read() {
      if (position >= limit) {
        return -1;
      }
      byte b = segment.get(ValueLayout.JAVA_BYTE, position);
      position++;
      return b & 0xff;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) {
      Objects.checkFromIndexSize(offset, length, bytes.length);
      if (length == 0) return 0;
      if (position == limit) return -1;
      int count = Math.min(length, limit - position);
      MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, position, bytes, offset, count);
      position += count;
      return count;
    }

    @Override
    public long skip(long count) {
      int skipped = Math.clamp(count, 0, limit - position);
      position += skipped;
      return skipped;
    }

    @Override
    public int available() {
      return limit - position;
    }

    @Override
    public boolean markSupported() {
      return true;
    }

    @Override
    public void mark(int readLimit) {
      mark = position;
    }

    @Override
    public void reset() {
      position = mark;
    }
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
