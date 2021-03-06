// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

public final class MathUtil {
  /**
   * Returns the closest non-negative {@code int} value to the absolute value of {@code a}. Works the same way as {@link Math#abs(int)} for all values except {@link Integer#MIN_VALUE},
   * for which {@link Math#abs(int)} returns negative value but this method returns {@link Integer#MAX_VALUE}.
   * <p/>
   * Use this method instead of {@link Math#abs(int) the standard one} if it's important to get non-negative result and {@link Integer#MIN_VALUE}
   * may be passed as an argument (e.g. if the argument is a result of {@link Object#hashCode()} or {@link java.util.Random#nextInt()} call).
   */
  public static int nonNegativeAbs(int a) {
    return a >= 0 ? a :
           a == Integer.MIN_VALUE ? Integer.MAX_VALUE : -a;
  }

  /**
   * Clamps the value to fit between min and max
   * @param value value to clamp
   * @param min min allowed value
   * @param max max allowed value
   * @return a clamped value that fits into [min..max] interval
   * @throws IllegalArgumentException if min &gt; max
   */
  public static int clamp(int value, int min, int max) {
    if (min > max) {
      throw new IllegalArgumentException(min + ">" + max);
    }
    return Math.min(max, Math.max(value, min));
  }

  /**
   * Clamps the value to fit between min and max
   * @param value value to clamp
   * @param min min allowed value
   * @param max max allowed value
   * @return a clamped value that fits into [min..max] interval
   * @throws IllegalArgumentException if min &gt; max
   */
  public static long clamp(long value, long min, long max) {
    if (min > max) {
      throw new IllegalArgumentException(min + ">" + max);
    }
    return Math.min(max, Math.max(value, min));
  }

  /**
   * Clamps the value to fit between min and max
   * @param value value to clamp
   * @param min min allowed value
   * @param max max allowed value
   * @return a clamped value that fits into [min..max] interval
   * @throws IllegalArgumentException if min &gt; max
   */
  public static double clamp(double value, double min, double max) {
    if (min > max) {
      throw new IllegalArgumentException(min + ">" + max);
    }
    return Math.min(max, Math.max(value, min));
  }

  /**
   * Clamps the value to fit between min and max
   * @param value value to clamp
   * @param min min allowed value
   * @param max max allowed value
   * @return a clamped value that fits into [min..max] interval
   * @throws IllegalArgumentException if min &gt; max
   */
  public static float clamp(float value, float min, float max) {
    if (min > max) {
      throw new IllegalArgumentException(min + ">" + max);
    }
    return Math.min(max, Math.max(value, min));
  }
}
