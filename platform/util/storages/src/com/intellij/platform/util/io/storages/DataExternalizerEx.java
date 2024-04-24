// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages;
import com.intellij.util.io.*;
import com.intellij.util.io.blobstorage.ByteBufferWriter;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Analog of {@link com.intellij.util.io.DataExternalizer}, but with {@link ByteBuffer} instead of
 * {@link java.io.InputStream} and {@link java.io.OutputStream}
 */
public interface DataExternalizerEx<T> {

  T read(@NotNull ByteBuffer input) throws IOException;

  KnownSizeRecordWriter writerFor(@NotNull T value) throws IOException;

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

        return new ByteArrayWriter(stream.toByteArray());
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
  }

  static KnownSizeRecordWriter fromBytes(byte @NotNull [] bytes) {
    return new ByteArrayWriter(bytes);
  }

  //MAYBE RC: append marker-interface, like ByteArrayExposingWriter,
  //          so implementation could use appendOnlyLog.append(byte[])
  //          method for such writers, which is slightly faster/cheaper
  /** Simplest implementation: writer over the record already serialized into a byte[] */
  class ByteArrayWriter implements KnownSizeRecordWriter {
    private final byte[] bytes;

    public ByteArrayWriter(byte @NotNull [] bytes) {
      this.bytes = bytes;
    }

    @Override
    public ByteBuffer write(@NotNull ByteBuffer data) throws IOException {
      return data.put(bytes);
    }

    @Override
    public int recordSize() {
      return bytes.length;
    }

    @Override
    public String toString(){
      return "ByteArrayWriter[" + IOUtil.toHexString(bytes) + "]";
    }
  }
}
