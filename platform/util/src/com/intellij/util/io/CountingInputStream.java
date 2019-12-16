// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

public class CountingInputStream extends InputStream {
  private final InputStream myInputStream;
  private long myBytesRead = 0;

  public CountingInputStream(@NotNull InputStream inputStream) {
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

  public long getBytesRead() {
    return myBytesRead;
  }
}
