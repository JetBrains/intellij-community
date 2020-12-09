// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class LimitedInputStream extends FilterInputStream {
  private final int myReadLimit;
  private int myBytesRead;

  public LimitedInputStream(final InputStream in, final int readLimit) {
    super(in);
    myReadLimit = readLimit;
    myBytesRead = 0;
  }

  @Override
  public boolean markSupported() {
    return false;
  }

  @Override
  public int read() throws IOException {
    if (remainingLimit() <= 0 ) return -1;
    final int r = super.read();
    if (r >= 0) myBytesRead++;
    return r;
  }

  @Override
  public int read(byte[] b) throws IOException {
    return read(b, 0, b.length);
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    if (len == 0) return 0;

    if (remainingLimit() <= 0) return -1;
    len = Math.min(len, remainingLimit());
    if (len <= 0) return -1;

    final int actuallyRead = super.read(b, off, len);
    if (actuallyRead >= 0) myBytesRead += actuallyRead;

    return actuallyRead;
  }

  @Override
  public long skip(long n) throws IOException {
    n = Math.min(n, remainingLimit());
    if (n <= 0) return 0;

    final long skipped = super.skip(n);
    myBytesRead += skipped;
    return skipped;
  }

  @Override
  public int available() throws IOException {
    return Math.min(super.available(), remainingLimit());
  }

  protected int remainingLimit() {
    return myReadLimit - myBytesRead;
  }

  @Override
  public synchronized void mark(final int readLimit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public synchronized void reset() throws IOException {
    throw new UnsupportedOperationException();
  }
}
