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

import com.intellij.openapi.util.io.ByteArraySequence;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class UnsyncByteArrayOutputStream extends OutputStream {
  protected byte[] myBuffer;
  protected int myCount;
  private boolean myIsShared;

  public UnsyncByteArrayOutputStream() {
    this(32);
  }

  public UnsyncByteArrayOutputStream(int size) {
    this(new byte[size]);
  }
  public UnsyncByteArrayOutputStream(byte[] buffer) {
    myBuffer = buffer;
  }

  @Override
  public void write(int b) {
    int newCount = myCount + 1;
    if (newCount > myBuffer.length || myIsShared) {
      grow(newCount);
      myIsShared = false;
    }
    myBuffer[myCount] = (byte)b;
    myCount = newCount;
  }

  private void grow(int newCount) {
    myBuffer = Arrays.copyOf(myBuffer, newCount > myBuffer.length ? Math.max(myBuffer.length << 1, newCount) : myBuffer.length);
  }

  @Override
  public void write(@NotNull byte[] b, int off, int len) {
    if (off < 0 || off > b.length || len < 0 ||
        off + len > b.length || off + len < 0) {
      throw new IndexOutOfBoundsException();
    }
    if (len == 0) {
      return;
    }
    int newCount = myCount + len;
    if (newCount > myBuffer.length || myIsShared) {
      grow(newCount);
      myIsShared = false;
    }
    System.arraycopy(b, off, myBuffer, myCount, len);
    myCount = newCount;
  }

  public void writeTo(OutputStream out) throws IOException {
    out.write(myBuffer, 0, myCount);
  }

  public void reset() {
    myCount = 0;
  }

  public byte[] toByteArray() {
    if (myBuffer.length == myCount) {
      myIsShared = true;
      return myBuffer;
    }
    return Arrays.copyOf(myBuffer, myCount);
  }

  public int size() {
    return myCount;
  }

  @Override
  public String toString() {
    return new String(myBuffer, 0, myCount);
  }

  @NotNull
  public ByteArraySequence toByteArraySequence() {
    return new ByteArraySequence(myBuffer, 0, myCount);
  }

  @NotNull
  public InputStream toInputStream() {
    return new UnsyncByteArrayInputStream(myBuffer, 0, myCount);
  }
}
