/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.util.io.zip;

/**
 * Utility class that represents a two byte integer with conversion
 * rules for the big endian byte order of ZIP files.
 */
public final class ZipShort implements Cloneable {
  private static final int BYTE_MASK = 0xFF;
  private static final int BYTE_1_MASK = 0xFF00;
  private static final int BYTE_1_SHIFT = 8;

  private int value;

  /**
   * Create instance from a number.
   *
   * @param value the int to store as a ZipShort
   * @since 1.1
   */
  public ZipShort(int value) {
    this.value = value;
  }

  /**
   * Create instance from bytes.
   *
   * @param bytes the bytes to store as a ZipShort
   * @since 1.1
   */
  public ZipShort(byte[] bytes) {
    this(bytes, 0);
  }

  /**
   * Create instance from the two bytes starting at offset.
   *
   * @param bytes  the bytes to store as a ZipShort
   * @param offset the offset to start
   * @since 1.1
   */
  public ZipShort(byte[] bytes, int offset) {
    value = getValue(bytes, offset);
  }

  /**
   * Get value as two bytes in big endian byte order.
   *
   * @return the value as a a two byte array in big endian byte order
   * @since 1.1
   */
  public byte[] getBytes() {
    byte[] result = new byte[2];
    result[0] = (byte)(value & BYTE_MASK);
    result[1] = (byte)((value & BYTE_1_MASK) >> BYTE_1_SHIFT);
    return result;
  }

  /**
   * Get value as Java int.
   *
   * @return value as a Java int
   * @since 1.1
   */
  public int getValue() {
    return value;
  }

  /**
   * Get value as two bytes in big endian byte order.
   *
   * @param value the Java int to convert to bytes
   * @return the converted int as a byte array in big endian byte order
   */
  public static byte[] getBytes(int value) {
    byte[] result = new byte[2];
    result[0] = (byte)(value & BYTE_MASK);
    result[1] = (byte)((value & BYTE_1_MASK) >> BYTE_1_SHIFT);
    return result;
  }

  /**
   * Helper method to get the value as a java int from two bytes starting at given array offset
   *
   * @param bytes  the array of bytes
   * @param offset the offset to start
   * @return the corresponding java int value
   */
  public static int getValue(byte[] bytes, int offset) {
    int value = (bytes[offset + 1] << BYTE_1_SHIFT) & BYTE_1_MASK;
    value += (bytes[offset] & BYTE_MASK);
    return value;
  }

  /**
   * Helper method to get the value as a java int from a two-byte array
   *
   * @param bytes the array of bytes
   * @return the corresponding java int value
   */
  public static int getValue(byte[] bytes) {
    return getValue(bytes, 0);
  }

  /**
   * Override to make two instances with same value equal.
   *
   * @param o an object to compare
   * @return true if the objects are equal
   * @since 1.1
   */
  public boolean equals(Object o) {
    if (!(o instanceof ZipShort)) {
      return false;
    }
    return value == ((ZipShort)o).getValue();
  }

  /**
   * Override to make two instances with same value equal.
   *
   * @return the value stored in the ZipShort
   * @since 1.1
   */
  public int hashCode() {
    return value;
  }
}
