// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * This implementation is <em>not</em> thread safe.
 */
@SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
public final class CountingInputStream extends FilterInputStream {

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized") private long myBytesRead = 0;
  private long myMark = -1;

  public CountingInputStream(@NotNull InputStream inputStream) {
    super(inputStream);
  }

  @Override
  public int read() throws IOException {
    int bytesRead = in.read();
    if (bytesRead != -1) {
      myBytesRead++;
    }
    return bytesRead;
  }

  @Override
  public int read(byte @NotNull [] b, int off, int len) throws IOException {
    int bytesRead = in.read(b, off, len);
    if (bytesRead != -1) {
      myBytesRead += bytesRead;
    }
    return bytesRead;
  }

  @Override
  public long skip(long n) throws IOException {
    long bytesSkipped = in.skip(n);
    myBytesRead += bytesSkipped;
    return bytesSkipped;
  }

  @Override
  public synchronized void mark(int readlimit) {
    in.mark(readlimit);
    myMark = myBytesRead;
  }

  @Override
  public synchronized void reset() throws IOException {
    if (!in.markSupported()) {
      throw new IOException("Mark not supported");
    }
    if (myMark == -1) {
      throw new IOException("Mark not set");
    }

    in.reset();
    myBytesRead = myMark;
  }

  public long getBytesRead() {
    return myBytesRead;
  }
}
