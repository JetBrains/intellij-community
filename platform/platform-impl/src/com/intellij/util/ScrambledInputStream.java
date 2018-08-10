// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import java.io.IOException;
import java.io.InputStream;

public class ScrambledInputStream extends InputStream{
  private static final int MASK = ScrambledOutputStream.MASK;
  private final InputStream myOriginalStream;

  public ScrambledInputStream(InputStream originalStream) {
    myOriginalStream = originalStream;
  }

  @Override
  public int read() throws IOException {
    int b = myOriginalStream.read();
    if (b == -1) return -1;
    return b ^ MASK;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    int read = myOriginalStream.read(b, off, len);
    for(int i = 0; i < read; i++){
      b[off + i] ^= MASK;
    }
    return read;
  }

  @Override
  public long skip(long n) throws IOException {
    return myOriginalStream.skip(n);
  }

  @Override
  public int available() throws IOException {
    return myOriginalStream.available();
  }

  @Override
  public void close() throws IOException {
    myOriginalStream.close();
  }

  @Override
  public synchronized void mark(int readlimit) {
    myOriginalStream.mark(readlimit);
  }

  @Override
  public synchronized void reset() throws IOException {
    myOriginalStream.reset();
  }

  @Override
  public boolean markSupported() {
    return myOriginalStream.markSupported();
  }
}
