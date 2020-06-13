// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.Contract;

public final class BitUtil {
  @Contract(pure = true)
  public static boolean isSet(byte value, byte mask) {
    assertOneBitMask(mask);
    return (value & mask) == mask;
  }
  @Contract(pure = true)
  public static boolean isSet(int value, int mask) {
    assertOneBitMask(mask);
    return (value & mask) == mask;
  }
  @Contract(pure = true)
  public static boolean isSet(long flags, long mask) {
    assertOneBitMask(mask);
    return (flags & mask) == mask;
  }

  @Contract(pure = true)
  public static byte set(byte value, byte mask, boolean setBit) {
    assertOneBitMask(mask);
    return (byte)(setBit ? value | mask : value & ~mask);
  }

  @Contract(pure = true)
  public static int set(int value, int mask, boolean setBit) {
    assertOneBitMask(mask);
    return setBit ? value | mask : value & ~mask;
  }

  @Contract(pure = true)
  public static long set(long value, long mask, boolean setBit) {
    assertOneBitMask(mask);
    return setBit ? value | mask : value & ~mask;
  }

  @Contract(pure = true)
  public static byte clear(byte value, byte mask) {
    return set(value, mask, false);
  }
  @Contract(pure = true)
  public static int clear(int value, int mask) {
    return set(value, mask, false);
  }
  @Contract(pure = true)
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

  public static boolean isPowerOfTwo(int i) {
    return (i & (i - 1)) == 0;
  }
}