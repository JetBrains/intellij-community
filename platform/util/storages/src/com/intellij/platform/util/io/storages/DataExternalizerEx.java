// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.io.*;
import com.intellij.util.io.blobstorage.ByteBufferWriter;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Analog of {@link DataExternalizer}, but with {@link ByteBuffer} instead of
 * {@link java.io.InputStream} and {@link java.io.OutputStream}
 */
public interface DataExternalizerEx<T> {

  T read(@NotNull ByteBuffer input) throws IOException;

  KnownSizeRecordWriter writerFor(@NotNull T value) throws IOException;

  /** @return true if all the values are serialized into the same number of bytes */
  default boolean isRecordSizeConstant() {
    return recordSizeIfConstant() > 0;
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
  static <K> DataExternalizerEx<K> adapt(@NotNull DataExternalizer<K> oldSchoolDescriptor) {

    if (oldSchoolDescriptor instanceof @NotNull KeyDescriptor<K>) {
      return KeyDescriptorEx.adapt((KeyDescriptor<K>)oldSchoolDescriptor);
    }

    //Do not wrap, if oldSchoolDescriptor already implements new interface
    // -> allows for 'bilingual' implementation (that implements both old&new
    // ifaces) to work efficiently
    if (oldSchoolDescriptor instanceof DataExternalizerEx<?>) {
      //noinspection unchecked
      return (DataExternalizerEx<K>)oldSchoolDescriptor;
    }

    return new DataExternalizerEx<>() {
      // Serialization/deserialization just bridges between ByteBuffer and ByteArrayInput/OutputStream

      //MAYBE RC: one allocation & one copy could be removed by implementing something like ByteBufferBacked[Input|Output]Stream
      //          instead of UnsyncByteArray[Input|Output]Stream

      @Override
      public K read(@NotNull ByteBuffer input) throws IOException {
        int bytesAvailable = input.remaining();
        byte[] contentAsArray = new byte[bytesAvailable];
        input.get(contentAsArray);
        return oldSchoolDescriptor.read(new DataInputStream(new UnsyncByteArrayInputStream(contentAsArray)));
      }


      @Override
      public KnownSizeRecordWriter writerFor(@NotNull K key) throws IOException {
        UnsyncByteArrayOutputStream stream = new UnsyncByteArrayOutputStream(64);
        try (DataOutputStream os = new DataOutputStream(stream)) {
          oldSchoolDescriptor.save(os, key);
        }

        ByteArraySequence byteArraySequence = stream.toByteArraySequence();
        return new ByteArrayWriter(byteArraySequence.getInternalBuffer(),
                                   byteArraySequence.getOffset(),
                                   byteArraySequence.getLength());
      }

      @Override
      public String toString() {
        return "DataExternalizerAdapter[adapted: " + oldSchoolDescriptor + "]";
      }
    };
  }

  interface KnownSizeRecordWriter extends ByteBufferWriter {
    @Override
    ByteBuffer write(@NotNull ByteBuffer data) throws IOException;

    /** @return size of the record to be written by {@link #write(ByteBuffer)} */
    int recordSize();

    KnownSizeRecordWriter NOTHING = new KnownSizeRecordWriter() {
      @Override
      public ByteBuffer write(@NotNull ByteBuffer data) {
        return data;
      }

      @Override
      public int recordSize() {
        return 0;
      }
    };
  }

  static KnownSizeRecordWriter fromBytes(byte @NotNull [] bytes) {
    return new ByteArrayWriter(bytes, 0, bytes.length);
  }

  static KnownSizeRecordWriter fromBytes(@NotNull ByteArraySequence bytes) {
    return new ByteArrayWriter(
      bytes.getInternalBuffer(),
      bytes.getOffset(),
      bytes.getLength()
    );
  }

  static KnownSizeRecordWriter fromBytes(byte @NotNull [] bytes,
                                         int offset,
                                         int length) {
    return new ByteArrayWriter(bytes, offset, length);
  }

  /**
   * Some implementations could operate faster with {@code byte[]}, then with
   * generic {@link KnownSizeRecordWriter} -- give them an option to recognize
   * writers that are just wrappers around byte[]
   */
  interface ByteArrayExposingWriter extends KnownSizeRecordWriter {
  }

  /** Simplest implementation: writer over the record already serialized into a byte[] */
  class ByteArrayWriter implements ByteArrayExposingWriter {
    private final byte[] bytes;
    private final int startingOffset;
    private final int length;

    public ByteArrayWriter(byte @NotNull [] bytes) {
      this(bytes, 0, bytes.length);
    }

    public ByteArrayWriter(byte @NotNull [] bytes,
                           int startingOffset,
                           int length) {
      this.bytes = bytes;
      this.startingOffset = startingOffset;
      this.length = length;
    }

    @Override
    public ByteBuffer write(@NotNull ByteBuffer data) throws IOException {
      return data.put(bytes, startingOffset, length);
    }

    @Override
    public int recordSize() {
      return length;
    }

    @Override
    public String toString() {
      return "ByteArrayWriter[" + IOUtil.toHexString(Arrays.copyOfRange(bytes, startingOffset, startingOffset + length)) + "]";
    }
  }
}
