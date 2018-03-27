// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.openapi.util.io;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;

/**
 * A sequence of bytes backed by byte array (or sub-array).
 */
public class ByteArraySequence implements ByteSequence {
  private final byte[] myBytes;
  private final int myOffset;
  private final int myLen;

  public ByteArraySequence(@NotNull byte[] bytes) {
    this(bytes, 0, bytes.length);
  }

  public ByteArraySequence(@NotNull byte[] bytes, int offset, int len) {
    myBytes = bytes;
    myOffset = offset;
    myLen = len;
    if (offset < 0 || offset > bytes.length || offset+len > bytes.length || len < 0) {
      throw new IllegalArgumentException("Offset is out of range: " + offset + "; bytes.length: " + bytes.length+"; len: "+len);
    }
  }

  /**
   * Implementation method.
   * @return Internal buffer, irrespective myOffset or myLen. May be larger than length().
   */
  @NotNull
  public byte[] getBytes() {
    return myBytes;
  }

  /**
   * Implementation method.
   * @return the offset of the area used for storing bytes in internal buffer.
   */
  public int getOffset() {
    return myOffset;
  }

  /**
   * Implementation method.
   * @return the length of area used for storing bytes in internal buffer.
   */
  public int getLength() {
    return myLen;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ByteArraySequence sequence = (ByteArraySequence)o;
    int len = myLen;
    if (len != sequence.myLen) return false;

    final byte[] thisBytes = myBytes;
    final byte[] thatBytes = sequence.myBytes;
    for (int i = 0, j = myOffset, k = sequence.myOffset; i < len; i++, j++, k++) {
      if (thisBytes[j] != thatBytes[k]) {
        return false;
      }
    }

    return true;
  }

  @Override
  public int hashCode() {
    final int len = myLen;
    final byte[] thisBytes = myBytes;

    int result = 1;
    for (int i = 0, j = myOffset; i < len; i++, j++) {
      result = result * 31 + thisBytes[j];
    }
    return result;
  }

  @Override
  public int length() {
    return getLength();
  }

  @Override
  public byte byteAt(int index) {
    return myBytes[myOffset + index];
  }

  @NotNull
  @Override
  public ByteSequence subSequence(int start, int end) {
    return new ByteArraySequence(myBytes, myOffset+start, end-start);
  }

  @NotNull
  @Override
  public byte[] toBytes() {
    byte[] bytes = new byte[length()];
    System.arraycopy(myBytes, myOffset, bytes, 0, length());
    return bytes;
  }

  @NotNull
  public ByteBuffer toByteBuffer() {
    return ByteBuffer.wrap(getBytes(), getOffset(), getLength());
  }
}
