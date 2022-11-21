// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

/**
 * This class intended for working with the alternative representation of {@link TextRange} as a long value, which logically consists of two int parts for startOffset and endOffset.
 * It might be useful to address atomicity or memory concerns.
 */
public class TextRangeScalarUtil {
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
    int newStart = startOffset(range) + deltaStart;
    int newEnd = endOffset(range) + deltaEnd;
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

  @NotNull
  public static TextRange create(long range) {
    return TextRange.create(startOffset(range), endOffset(range));
  }
  public static boolean equalsToRange(@NotNull TextRange thisRange, long otherRange) {
    return thisRange.getStartOffset() == startOffset(otherRange) && thisRange.getEndOffset() == endOffset(otherRange);
  }
}
