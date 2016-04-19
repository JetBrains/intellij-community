/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util;

public class BitUtil {
  public static boolean isSet(byte value, byte mask) {
    assertOneBitMask(mask);
    return (value & mask) == mask;
  }
  public static boolean isSet(int value, int mask) {
    assertOneBitMask(mask);
    return (value & mask) == mask;
  }
  public static boolean isSet(long flags, long mask) {
    assertOneBitMask(mask);
    return (flags & mask) == mask;
  }

  public static byte set(byte value, byte mask, boolean setBit) {
    assertOneBitMask(mask);
    return (byte)(setBit ? value | mask : value & ~mask);
  }
  public static int set(int value, int mask, boolean setBit) {
    assertOneBitMask(mask);
    return setBit ? value | mask : value & ~mask;
  }
  public static long set(long value, long mask, boolean setBit) {
    assertOneBitMask(mask);
    return setBit ? value | mask : value & ~mask;
  }

  public static byte clear(byte value, byte mask) {
    return set(value, mask, false);
  }
  public static int clear(int value, int mask) {
    return set(value, mask, false);
  }
  public static long clear(long value, long mask) {
    return set(value, mask, false);
  }

  private static void assertOneBitMask(byte mask) {
    assertOneBitMask(mask & 0xFFL);
  }
  public static void assertOneBitMask(int mask) {
    assertOneBitMask(mask & 0xFFFFFFFFL);
  }
  private static void assertOneBitMask(long mask) {
    assert (mask & mask - 1) == 0 : "Mask must have only one bit set, but got: " + Long.toBinaryString(mask);
  }
}