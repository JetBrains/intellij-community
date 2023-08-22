// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.text;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

public final class Formats {
  /** Formats given file size in metric (1 kB = 1000 B) units (example: {@code formatFileSize(1234) = "1.23 KB"}). */
  @Contract(pure = true)
  public static @NotNull String formatFileSize(long fileSize) {
    return StringUtilRt.formatFileSize(fileSize);
  }

  /** Formats given file size in metric (1 kB = 1000 B) units (example: {@code formatFileSize(1234, "") = "1.23KB"}). */
  @Contract(pure = true)
  public static @NotNull String formatFileSize(long fileSize, @NotNull String unitSeparator) {
    return StringUtilRt.formatFileSize(fileSize, unitSeparator);
  }

  /** Formats duration given in milliseconds as a sum of time units (example: {@code formatDuration(123456) = "2 m 3 s 456 ms"}). */
  @Contract(pure = true)
  public static @NotNull String formatDuration(long duration) {
    return formatDuration(duration, " ");
  }

  /** Formats {@link Duration} as a sum of time units (calls {@link #formatDuration(long)} with duration converted to milliseconds) */
  @Contract(pure = true)
  public static @NotNull String formatDuration(@NotNull Duration duration) {
    return formatDuration(duration.toMillis());
  }

  private static final String[] TIME_UNITS = {"ms", "s", "m", "h", "d"};
  private static final long[] TIME_MULTIPLIERS = {1, 1000, 60, 60, 24};

  /** Formats duration given in milliseconds as a sum of time units (example: {@code formatDuration(123456, "") = "2m 3s 456ms"}). */
  @Contract(pure = true)
  public static @NotNull String formatDuration(long duration, @NotNull String unitSeparator) {
    return formatDuration(duration, unitSeparator, Integer.MAX_VALUE);
  }

  @Contract(pure = true)
  private static @NotNull String formatDuration(long duration, @NotNull String unitSeparator, int maxFragments) {
    LongList unitValues = new LongArrayList();
    IntList unitIndices = new IntArrayList();

    long count = duration;
    int i = 1;
    for (; i < TIME_UNITS.length && count > 0; i++) {
      long multiplier = TIME_MULTIPLIERS[i];
      if (count < multiplier) break;
      long remainder = count % multiplier;
      count /= multiplier;
      if (remainder != 0 || !unitValues.isEmpty()) {
        unitValues.add(0, remainder);
        unitIndices.add(0, i - 1);
      }
    }
    unitValues.add(0, count);
    unitIndices.add(0, i - 1);

    if (unitValues.size() > maxFragments) {
      int lastUnitIndex = unitIndices.getInt(maxFragments - 1);
      long lastMultiplier = TIME_MULTIPLIERS[lastUnitIndex];
      // Round up if needed
      if (unitValues.getLong(maxFragments) > lastMultiplier / 2) {
        long increment = lastMultiplier - unitValues.getLong(maxFragments);
        for (int unit = lastUnitIndex - 1; unit > 0; unit--) {
          increment *= TIME_MULTIPLIERS[unit];
        }
        return formatDuration(duration + increment, unitSeparator, maxFragments);
      }
    }

    StringBuilder result = new StringBuilder();
    for (i = 0; i < unitValues.size() && i < maxFragments; i++) {
      if (i > 0) result.append(" ");
      result.append(unitValues.getLong(i)).append(unitSeparator).append(TIME_UNITS[unitIndices.getInt(i)]);
    }
    return result.toString();
  }
}
