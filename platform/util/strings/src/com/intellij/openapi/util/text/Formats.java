// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.text;

import com.intellij.util.text.OrdinalFormat;
import gnu.trove.TIntArrayList;
import gnu.trove.TLongArrayList;
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
    TLongArrayList unitValues = new TLongArrayList();
    TIntArrayList unitIndices = new TIntArrayList();

    long count = duration;
    int i = 1;
    for (; i < TIME_UNITS.length && count > 0; i++) {
      long multiplier = TIME_MULTIPLIERS[i];
      if (count < multiplier) break;
      long remainder = count % multiplier;
      count /= multiplier;
      if (remainder != 0 || !unitValues.isEmpty()) {
        unitValues.insert(0, remainder);
        unitIndices.insert(0, i - 1);
      }
    }
    unitValues.insert(0, count);
    unitIndices.insert(0, i - 1);

    if (unitValues.size() > maxFragments) {
      int lastUnitIndex = unitIndices.get(maxFragments - 1);
      long lastMultiplier = TIME_MULTIPLIERS[lastUnitIndex];
      // Round up if needed
      if (unitValues.get(maxFragments) > lastMultiplier / 2) {
        long increment = lastMultiplier - unitValues.get(maxFragments);
        for (int unit = lastUnitIndex - 1; unit > 0; unit--) {
          increment *= TIME_MULTIPLIERS[unit];
        }
        return formatDuration(duration + increment, unitSeparator, maxFragments);
      }
    }

    StringBuilder result = new StringBuilder();
    for (i = 0; i < unitValues.size() && i < maxFragments; i++) {
      if (i > 0) result.append(" ");
      result.append(unitValues.get(i)).append(unitSeparator).append(TIME_UNITS[unitIndices.get(i)]);
    }
    return result.toString();
  }

  private static final String[] PADDED_FORMATS = {"%03d", "%02d", "%02d", "%02d", "%d"};
  /**
   * Formats duration given in milliseconds as a sum of padded time units, except the most significant unit
   * E.g. 234523598 padded as "2 d 03 h 11 min 04 sec 004 ms" accordingly with zeros except "days" here.
   */
  @Contract(pure = true)
  public static @NotNull String formatDurationPadded(long millis, @NotNull String unitSeparator) {
    StringBuilder result = new StringBuilder();

    long millisIn = 1;
    int i;
    for (i=1; i < TIME_MULTIPLIERS.length; i++) {
      long multiplier = TIME_MULTIPLIERS[i];
      millisIn *= multiplier;
      if (millis < millisIn) {
        break;
      }
    }
    long d = millis;
    for (i-=1; i >= 0; i--) {
      long multiplier = i==TIME_MULTIPLIERS.length-1 ? 1 : TIME_MULTIPLIERS[i+1];
      millisIn /= multiplier;
      long value = d / millisIn;
      d = d % millisIn;
      String format = result.length() == 0 ? "%d" : PADDED_FORMATS[i]; // do not pad the most significant unit
      if (result.length() != 0) result.append(" ");
      result.append(String.format(format, value)).append(unitSeparator).append(TIME_UNITS[i]);
    }
    return result.toString();
  }

  /**
   * Formats duration given in milliseconds as a sum of time units with at most two units
   * (example: {@code formatDuration(123456) = "2 m 3 s"}).
   */
  @Contract(pure = true)
  public static @NotNull String formatDurationApproximate(long duration) {
    return formatDuration(duration, " ", 2);
  }

  /**
   * Appends English ordinal suffix to the given number.
   */
  @Contract(pure = true)
  public static @NotNull String formatOrdinal(long num) {
    return OrdinalFormat.formatEnglish(num);
  }
}
