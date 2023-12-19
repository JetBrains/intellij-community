// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.dev.enumerator;

import com.intellij.util.io.IOUtil;
import com.intellij.util.io.blobstorage.ByteBufferWriter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Analog of {@link com.intellij.util.io.DataExternalizer}, but with {@link ByteBuffer} instead of
 * {@link java.io.InputStream} and {@link java.io.OutputStream}
 */
public interface DataExternalizerEx<T> {

  T read(@NotNull ByteBuffer input) throws IOException;

  KnownSizeRecordWriter writerFor(@NotNull T value) throws IOException;

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
