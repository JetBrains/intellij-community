/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;

import java.io.InputStream;

public class UnsyncByteArrayInputStream extends InputStream {
  protected byte[] myBuffer;
  private int myPosition;
  private int myCount;
  private int myMarkedPosition;

  public UnsyncByteArrayInputStream(@NotNull byte[] buf) {
    this(buf, 0, buf.length);
  }

  public UnsyncByteArrayInputStream(byte[] buf, int offset, int length) {
    init(buf, offset, length);
  }

  public void init(byte[] buf, int offset, int length) {
    myBuffer = buf;
    myPosition = offset;
    myCount = length;
  }

  @Override
  public int read() {
    return myPosition < myCount ? myBuffer[myPosition++] & 0xff : -1;
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
  public void mark(int readlimit) {
    myMarkedPosition = myPosition;
  }

  @Override
  public void reset() {
    myPosition = myMarkedPosition;
  }
}
