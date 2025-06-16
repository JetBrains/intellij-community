// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.util.MathUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * This class contains utility methods working with the alternative representation of {@link TextRange} as a {@code long} value,
 * which logically consists of two {@code int} parts: for {@link TextRange#myStartOffset} and {@link TextRange#myEndOffset}.
 * It might be useful to address atomicity or memory concerns.
 */
@ApiStatus.Internal
public final class TextRangeScalarUtil {
  public static long toScalarRange(@NotNull Segment range) {
    return toScalarRange(range.getStartOffset(), range.getEndOffset());
  }

  public static long toScalarRange(int start, int end) {
    if (start > end || start < 0) {
      throw new IllegalArgumentException("Incorrect offsets: start="+start+"; end="+end);
    }
    return ((long)end << 32) | start;
  }

  public static long shift(long range, int deltaStart, int deltaEnd) {
    int newEnd = Math.max(endOffset(range) + deltaEnd, 0);
    int newStart = MathUtil.clamp(startOffset(range) + deltaStart, 0, newEnd);

    return toScalarRange(newStart, newEnd);
  }

  public static long union(long range1, long range2) {
    if (range1 == range2) return range1;
    int start = Math.min(startOffset(range1), startOffset(range2));
    int end = Math.max(endOffset(range1), endOffset(range2));
    return toScalarRange(start, end);
  }

  public static int startOffset(long range) {
    return (int)range & 0x7fffffff;
  }

  public static int endOffset(long range) {
    return (int)(range >>> 32);
  }

  public static boolean contains(long outerRange, long innerRange) {
    return containsRange(outerRange, startOffset(innerRange), endOffset(innerRange));
  }

  public static boolean containsRange(long outerRange, int innerRangeStartOffset, int innerRangeEndOffset) {
    return startOffset(outerRange) <= innerRangeStartOffset && innerRangeEndOffset <= endOffset(outerRange);
  }
  public static boolean containsOffset(long range, int offset) {
    return startOffset(range) <= offset && offset <= endOffset(range);
  }
  public static boolean intersects(@NotNull TextRange thisRange, long otherRange) {
    return thisRange.intersects(startOffset(otherRange), endOffset(otherRange));
  }

  public static @NotNull TextRange create(long range) {
    return TextRange.create(startOffset(range), endOffset(range));
  }

  /**
   * @return {@code range} coerced to be within ({@code requiredStart}, {@code requiredEnd})
   */
  public static long coerceRange(long range, int requiredStart, int requiredEnd) {
    return coerceRange(startOffset(range), endOffset(range), requiredStart, requiredEnd);
  }
  public static long coerceRange(int start, int end, int requiredStart, int requiredEnd) {
    assert requiredStart >= 0 && requiredStart <= requiredEnd : "requiredStart:" + requiredStart + ", requiredEnd=" + requiredEnd;
    int newStart = coerce(start, requiredStart, requiredEnd);
    int newEnd = coerce(end, newStart, requiredEnd);
    return toScalarRange(newStart, newEnd);
  }

  /**
   * @return offset guaranteed to be within (requiredStart, requiredEnd)
   */
  public static int coerce(int offset, int requiredStart, int requiredEnd) {
    assert requiredStart >= 0 && requiredStart <= requiredEnd : "requiredStart:" + requiredStart + ", requiredEnd=" + requiredEnd;
    return Math.min(Math.max(offset, requiredStart), requiredEnd);
  }
}
