/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

public class UnsyncByteArrayOutputStream extends OutputStream {
  protected byte[] myBuffer;
  protected int myCount;

  public UnsyncByteArrayOutputStream() {
    this(32);
  }

  public UnsyncByteArrayOutputStream(int size) {
    myBuffer = new byte[size];
  }

  @Override
  public void write(int b) {
    int newcount = myCount + 1;
    if (newcount > myBuffer.length) {
      myBuffer = Arrays.copyOf(myBuffer, Math.max(myBuffer.length << 1, newcount));
    }
    myBuffer[myCount] = (byte)b;
    myCount = newcount;
  }

  @Override
  public void write(byte b[], int off, int len) {
    if ((off < 0) || (off > b.length) || (len < 0) ||
        ((off + len) > b.length) || ((off + len) < 0)) {
      throw new IndexOutOfBoundsException();
    } else if (len == 0) {
      return;
    }
    int newcount = myCount + len;
    if (newcount > myBuffer.length) {
      myBuffer = Arrays.copyOf(myBuffer, Math.max(myBuffer.length << 1, newcount));
    }
    System.arraycopy(b, off, myBuffer, myCount, len);
    myCount = newcount;
  }

  public void writeTo(OutputStream out) throws IOException {
    out.write(myBuffer, 0, myCount);
  }

  public void reset() {
    myCount = 0;
  }

  public byte[] toByteArray() {
    return Arrays.copyOf(myBuffer, myCount);
  }

  public int size() {
    return myCount;
  }

  public String toString() {
    return new String(myBuffer, 0, myCount);
  }
}
