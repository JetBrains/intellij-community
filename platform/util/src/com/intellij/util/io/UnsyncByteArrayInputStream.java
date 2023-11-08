// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;

import java.io.InputStream;

@SuppressWarnings("NonSynchronizedMethodOverridesSynchronizedMethod")
public class UnsyncByteArrayInputStream extends InputStream {
  protected final byte[] myBuffer;
  private int myPosition;
  private final int myCount;
  private int myMarkedPosition;

  public UnsyncByteArrayInputStream(@NotNull byte[] buf) {
    this(buf, 0, buf.length);
  }

  public UnsyncByteArrayInputStream(@NotNull byte[] buf, int offset, int length) {
    myBuffer = buf;
    myPosition = offset;
    myCount = Math.min(offset + length, buf.length);
    myMarkedPosition = offset;
  }

  @Override
  public int read() {
    return myPosition < myCount ? myBuffer[myPosition++] & 0xff : -1;
  }

  // read next two bytes and convert them to short (little endian)
  public int readShortLittleEndian() {
    int position = myPosition;
    if (position >= myCount - 1) {
      return -1;
    }
    byte ch1 = myBuffer[position];
    byte ch2 = myBuffer[position+1];
    myPosition += 2;
    return (ch1 & 0xff) | ((ch2 << 8) & 0xff00);
  }

  @Override
  public int read(@NotNull byte[] b, int off, int len) {
    if (off < 0 || len < 0 || len > b.length - off) {
      throw new IndexOutOfBoundsException();
    }
    if (myPosition >= myCount) {
      return -1;
    }
    if (myPosition + len > myCount) {
      len = myCount - myPosition;
    }
    if (len <= 0) {
      return 0;
    }
    System.arraycopy(myBuffer, myPosition, b, off, len);
    myPosition += len;
    return len;
  }

  @Override
  public long skip(long n) {
    if (myPosition + n > myCount) {
      n = myCount - myPosition;
    }
    if (n < 0) {
      return 0;
    }
    myPosition += n;
    return n;
  }

  @Override
  public int available() {
    return myCount - myPosition;
  }

  @Override
  public boolean markSupported() {
    return true;
  }

  @Override
  public void mark(int readLimit) {
    myMarkedPosition = myPosition;
  }

  @Override
  public void reset() {
    myPosition = myMarkedPosition;
  }

  @Override
  public String toString() {
    return getClass() + " (" + available() + " bytes available out of " + myCount + ")";
  }
}