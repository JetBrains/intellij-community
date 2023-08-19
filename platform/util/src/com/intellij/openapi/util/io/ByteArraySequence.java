// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import com.intellij.util.ArrayUtil;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.util.Arrays;

/**
 * A sequence of bytes backed by byte array (or sub-array).
 */
public final class ByteArraySequence implements ByteSequence {
  public static final ByteArraySequence EMPTY = new ByteArraySequence(ArrayUtil.EMPTY_BYTE_ARRAY);
  private final byte[] myBytes;
  private final int myOffset;
  private final int myLen;

  public ByteArraySequence(byte @NotNull [] bytes) {
    this(bytes, 0, bytes.length);
  }

  public ByteArraySequence(byte @NotNull [] bytes, int offset, int len) {
    myBytes = bytes;
    myOffset = offset;
    myLen = len;
    if (offset < 0 || offset > bytes.length || offset+len > bytes.length || len < 0) {
      throw new IllegalArgumentException("Offset is out of range: " + offset + "; bytes.length: " + bytes.length+"; len: "+len);
    }
  }

  /**
   * Implementation method.
   * @return Internal buffer, irrespective of myOffset or myLen. May be larger than length().
   */
  @ApiStatus.Internal
  public byte @NotNull [] getInternalBuffer() {
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

  @Override
  public @NotNull ByteSequence subSequence(int start, int end) {
    return new ByteArraySequence(myBytes, myOffset+start, end-start);
  }

  @Override
  public byte @NotNull [] toBytes() {
    return Arrays.copyOfRange(myBytes, myOffset, myOffset + length());
  }

  public @NotNull DataInputStream toInputStream() {
    return new DataInputStream(new UnsyncByteArrayInputStream(myBytes, myOffset, length()));
  }

  public static @NotNull ByteArraySequence create(byte @NotNull [] bytes) {
    return bytes.length == 0 ? ByteArraySequence.EMPTY : new ByteArraySequence(bytes);
  }
}
