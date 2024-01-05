// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.dev;

import org.jetbrains.annotations.NotNull;

/**
 *
 */
public final class AlignmentUtils {
  private AlignmentUtils() {
    throw new AssertionError("Not for instantiation");
  }

  //================== 32b alignment methods: ==========================================

  public static int roundUpToInt32(int value) {
    if (is32bAligned(value)) {
      return value;
    }
    return ((value >> 2) + 1) << 2;
  }

  public static long roundUpToInt32(long value) {
    if (is32bAligned(value)) {
      return value;
    }
    return ((value >> 2) + 1) << 2;
  }

  public static boolean is32bAligned(int value) {
    return (value & 0b11) == 0;
  }

  public static boolean is32bAligned(long value) {
    return (value & 0b11L) == 0;
  }

  public static void assert32bAligned(long value,
                                      @NotNull String name) {
    if (!is32bAligned(value)) {
      throw new AssertionError("Bug: " + name + "(=" + value + ") is not 32b-aligned");
    }
  }

  public static void assert32bAligned(int value,
                                      @NotNull String name) {
    if (!is32bAligned(value)) {
      throw new AssertionError("Bug: " + name + "(=" + value + ") is not 32b-aligned");
    }
  }

  //================== 64b alignment methods: ==========================================

  public static int roundUpToInt64(int value) {
    if (is64bAligned(value)) {
      return value;
    }
    return ((value >> 3) + 1) << 3;
  }

  public static int roundDownToInt64(int value) {
    return (value & (~0b111));
  }

  public static long roundUpToInt64(long value) {
    if (is64bAligned(value)) {
      return value;
    }
    return ((value >> 3) + 1) << 3;
  }

  public static boolean is64bAligned(int value) {
    return (value & 0b111) == 0;
  }

  public static boolean is64bAligned(long value) {
    return (value & 0b111L) == 0;
  }

  public static void assert64bAligned(long value,
                                      @NotNull String name) {
    if (!is64bAligned(value)) {
      throw new AssertionError("Bug: " + name + "(=" + value + ") is not 64b-aligned");
    }
  }

  public static void assert64bAligned(int value,
                                      @NotNull String name) {
    if (!is64bAligned(value)) {
      throw new AssertionError("Bug: " + name + "(=" + value + ") is not 64b-aligned");
    }
  }
}
