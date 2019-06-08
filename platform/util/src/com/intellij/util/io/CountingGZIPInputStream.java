// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * A stream for reading compressed data in the GZIP file format.
 * Total amount of compressed read bytes can be accessed via {@link #getCompressedBytesRead()}.
 *
 * Note that this implementation is not thread safe.
 */
public final class CountingGZIPInputStream extends GZIPInputStream {
  private final CountingInputStream myInputStream;

  private CountingGZIPInputStream(@NotNull CountingInputStream inputStream) throws IOException {
    super(inputStream);
    myInputStream = inputStream;
  }

  public long getCompressedBytesRead() {
    return myInputStream.myBytesRead;
  }

  @NotNull
  public static CountingGZIPInputStream create(@NotNull InputStream inputStream) throws IOException {
    return new CountingGZIPInputStream(new CountingInputStream(inputStream));
  }

  private static class CountingInputStream extends InputStream {
    private final InputStream myInputStream;
    private long myBytesRead = 0;

    CountingInputStream(@NotNull InputStream inputStream) {
      myInputStream = inputStream;
    }

    @Override
    public int read() throws IOException {
      int data = myInputStream.read();
      myBytesRead++;
      return data;
    }

    @Override
    public int read(@NotNull byte[] b) throws IOException {
      int bytesRead = myInputStream.read(b);
      myBytesRead += bytesRead;
      return bytesRead;
    }

    @Override
    public int read(@NotNull byte[] b, int off, int len) throws IOException {
      int bytesRead = myInputStream.read(b, off, len);
      myBytesRead += bytesRead;
      return bytesRead;
    }

    @Override
    public long skip(long n) throws IOException {
      long bytesSkipped = myInputStream.skip(n);
      myBytesRead += bytesSkipped;
      return bytesSkipped;
    }

    @Override
    public int available() throws IOException {
      return myInputStream.available();
    }

    @Override
    public void close() throws IOException {
      myInputStream.close();
    }

    @Override
    public synchronized void mark(int readlimit) {
      myInputStream.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
      myInputStream.reset();
    }

    @Override
    public boolean markSupported() {
      return myInputStream.markSupported();
    }
  }
}
