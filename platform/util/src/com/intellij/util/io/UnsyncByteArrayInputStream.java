package com.intellij.util.io;

import java.io.InputStream;

public class UnsyncByteArrayInputStream extends InputStream {
  protected byte[] myBuffer;
  private int myPosition;
  private int myCount;
  private int myMarkedPosition;

  public UnsyncByteArrayInputStream(byte buf[]) {
    this.myBuffer = buf;
    this.myPosition = 0;
    this.myCount = buf.length;
  }

  public int read() {
    return (myPosition < myCount) ? (myBuffer[myPosition++] & 0xff) : -1;
  }

  public int read(byte b[], int off, int len) {
    if (b == null) {
      throw new NullPointerException();
    } else if (off < 0 || len < 0 || len > b.length - off) {
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

  public int available() {
    return myCount - myPosition;
  }

  @Override
  public boolean markSupported() {
    return true;
  }

  @Override
  public void mark(int readlimit) {
    myMarkedPosition = myPosition;
  }

  public void reset() {
    myPosition = myMarkedPosition;
  }
}
