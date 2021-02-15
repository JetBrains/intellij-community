// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.io.ByteArraySequence;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
  public void write(byte @NotNull [] b, int off, int len) {
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
    return new String(myBuffer, 0, myCount, StandardCharsets.UTF_8);
  }

  @NotNull
  public ByteArraySequence toByteArraySequence() {
    return myCount == 0 ? ByteArraySequence.EMPTY : new ByteArraySequence(myBuffer, 0, myCount);
  }

  @NotNull
  public InputStream toInputStream() {
    return new UnsyncByteArrayInputStream(myBuffer, 0, myCount);
  }
}
