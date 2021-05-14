// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.zip;

import java.math.BigInteger;

public class ZipUInt64 {
  private static final int BYTE_1 = 1;
  private static final int BYTE_1_MASK = 0xFF00;
  private static final int BYTE_1_SHIFT = 8;

  private static final int BYTE_2 = 2;
  private static final int BYTE_2_MASK = 0xFF0000;
  private static final int BYTE_2_SHIFT = 16;

  private static final int BYTE_3 = 3;
  private static final long BYTE_3_MASK = 0xFF000000L;
  private static final int BYTE_3_SHIFT = 24;

  private static final int BYTE_4 = 4;
  private static final long BYTE_4_MASK = 0xFF00000000L;
  private static final int BYTE_4_SHIFT = 32;

  private static final int BYTE_5 = 5;
  private static final long BYTE_5_MASK = 0xFF0000000000L;
  private static final int BYTE_5_SHIFT = 40;

  private static final int BYTE_6 = 6;
  private static final long BYTE_6_MASK = 0xFF000000000000L;
  private static final int BYTE_6_SHIFT = 48;

  private static final int BYTE_7 = 7;
  private static final long BYTE_7_MASK = 0x7F00000000000000L;
  private static final int BYTE_7_SHIFT = 56;

  private static final int LEFTMOST_BIT_SHIFT = 63;
  private static final byte LEFTMOST_BIT = (byte)0x80;

  static final int BYTE_MASK = 0xFF;

  private final BigInteger value;

  /**
   * Create instance from a number.
   *
   * @param value the long to store
   */
  public ZipUInt64(final long value) {
    this(BigInteger.valueOf(value));
  }

  /**
   * Create instance from a number.
   *
   * @param value the BigInteger to store
   */
  public ZipUInt64(final BigInteger value) {
    this.value = value;
  }

  /**
   * Create instance from bytes.
   *
   * @param bytes the bytes to store
   */
  public ZipUInt64(final byte[] bytes) {
    this(bytes, 0);
  }

  /**
   * Create instance from the eight bytes starting at offset.
   *
   * @param bytes  the bytes to store
   * @param offset the offset to start
   */
  public ZipUInt64(final byte[] bytes, final int offset) {
    value = getValue(bytes, offset);
  }

  /**
   * Get value as eight bytes in big endian byte order.
   *
   * @return value as eight bytes in big endian order
   */
  public byte[] getBytes() {
    return getBytes(value);
  }

  /**
   * Get value as Java long.
   *
   * @return value as a long
   */
  public long getLongValue() {
    return value.longValue();
  }

  /**
   * Get value as Java BigInteger.
   *
   * @return value as a BigInteger
   */
  public BigInteger getValue() {
    return value;
  }

  /**
   * Get value as eight bytes in big endian byte order.
   *
   * @param value the value to convert
   * @return value as eight bytes in big endian byte order
   */
  public static byte[] getBytes(final long value) {
    return getBytes(BigInteger.valueOf(value));
  }

  /**
   * Get value as eight bytes in big endian byte order.
   *
   * @param value the value to convert
   * @return value as eight bytes in big endian byte order
   */
  public static byte[] getBytes(final BigInteger value) {
    final byte[] result = new byte[8];
    final long val = value.longValue();
    result[0] = (byte)((val & BYTE_MASK));
    result[BYTE_1] = (byte)((val & BYTE_1_MASK) >> BYTE_1_SHIFT);
    result[BYTE_2] = (byte)((val & BYTE_2_MASK) >> BYTE_2_SHIFT);
    result[BYTE_3] = (byte)((val & BYTE_3_MASK) >> BYTE_3_SHIFT);
    result[BYTE_4] = (byte)((val & BYTE_4_MASK) >> BYTE_4_SHIFT);
    result[BYTE_5] = (byte)((val & BYTE_5_MASK) >> BYTE_5_SHIFT);
    result[BYTE_6] = (byte)((val & BYTE_6_MASK) >> BYTE_6_SHIFT);
    result[BYTE_7] = (byte)((val & BYTE_7_MASK) >> BYTE_7_SHIFT);
    if (value.testBit(LEFTMOST_BIT_SHIFT)) {
      result[BYTE_7] |= LEFTMOST_BIT;
    }
    return result;
  }

  /**
   * Helper method to get the value as a Java long from eight bytes
   * starting at given array offset
   *
   * @param bytes  the array of bytes
   * @param offset the offset to start
   * @return the corresponding Java long value
   */
  public static long getLongValue(final byte[] bytes, final int offset) {
    return getValue(bytes, offset).longValue();
  }

  /**
   * Helper method to get the value as a Java BigInteger from eight
   * bytes starting at given array offset
   *
   * @param bytes  the array of bytes
   * @param offset the offset to start
   * @return the corresponding Java BigInteger value
   */
  public static BigInteger getValue(final byte[] bytes, final int offset) {
    long value = ((long)bytes[offset + BYTE_7] << BYTE_7_SHIFT) & BYTE_7_MASK;
    value += ((long)bytes[offset + BYTE_6] << BYTE_6_SHIFT) & BYTE_6_MASK;
    value += ((long)bytes[offset + BYTE_5] << BYTE_5_SHIFT) & BYTE_5_MASK;
    value += ((long)bytes[offset + BYTE_4] << BYTE_4_SHIFT) & BYTE_4_MASK;
    value += ((long)bytes[offset + BYTE_3] << BYTE_3_SHIFT) & BYTE_3_MASK;
    value += ((long)bytes[offset + BYTE_2] << BYTE_2_SHIFT) & BYTE_2_MASK;
    value += ((long)bytes[offset + BYTE_1] << BYTE_1_SHIFT) & BYTE_1_MASK;
    value += ((long)bytes[offset] & BYTE_MASK);
    final BigInteger val = BigInteger.valueOf(value);
    return (bytes[offset + BYTE_7] & LEFTMOST_BIT) == LEFTMOST_BIT
           ? val.setBit(LEFTMOST_BIT_SHIFT) : val;
  }

  /**
   * Helper method to get the value as a Java long from an eight-byte array
   *
   * @param bytes the array of bytes
   * @return the corresponding Java long value
   */
  public static long getLongValue(final byte[] bytes) {
    return getLongValue(bytes, 0);
  }

  /**
   * Helper method to get the value as a Java long from an eight-byte array
   *
   * @param bytes the array of bytes
   * @return the corresponding Java BigInteger value
   */
  public static BigInteger getValue(final byte[] bytes) {
    return getValue(bytes, 0);
  }

  @Override
  public boolean equals(final Object o) {
    if (!(o instanceof ZipUInt64)) {
      return false;
    }
    return value.equals(((ZipUInt64)o).getValue());
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return "ZipInt64: " + value;
  }
}
