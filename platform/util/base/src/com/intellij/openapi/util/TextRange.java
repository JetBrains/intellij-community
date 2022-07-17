// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * A text range defined by start and end (exclusive) offset.
 *
 * @see ProperTextRange
 * @see com.intellij.util.text.TextRangeUtil
 */
public class TextRange implements Segment, Serializable {
  private static final long serialVersionUID = -670091356599757430L;

  public static final TextRange EMPTY_RANGE = new TextRange(0, 0);
  public static final TextRange[] EMPTY_ARRAY = new TextRange[0];

  private final int myStartOffset;
  private final int myEndOffset;

  /**
   * @see #create(int, int)
   * @see #from(int, int)
   * @see #allOf(String)
   */
  public TextRange(int startOffset, int endOffset) {
    this(startOffset, endOffset, true);
  }

  /**
   * @param checkForProperTextRange {@code true} if offsets should be checked by {@link #assertProperRange(int, int, Object)}
   * @see UnfairTextRange
   */
  protected TextRange(int startOffset, int endOffset, boolean checkForProperTextRange) {
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    if (checkForProperTextRange) {
      assertProperRange(this);
    }
  }

  @Override
  public final int getStartOffset() {
    return myStartOffset;
  }

  @Override
  public final int getEndOffset() {
    return myEndOffset;
  }

  public final int getLength() {
    return myEndOffset - myStartOffset;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof TextRange)) return false;
    TextRange range = (TextRange)obj;
    return myStartOffset == range.myStartOffset && myEndOffset == range.myEndOffset;
  }

  @Override
  public int hashCode() {
    return myStartOffset + myEndOffset;
  }

  public boolean contains(@NotNull TextRange range) {
    return contains((Segment)range);
  }

  public boolean contains(@NotNull Segment range) {
    return containsRange(range.getStartOffset(), range.getEndOffset());
  }

  public boolean containsRange(int startOffset, int endOffset) {
    return getStartOffset() <= startOffset && endOffset <= getEndOffset();
  }

  public static boolean containsRange(@NotNull Segment outer, @NotNull Segment inner) {
    return outer.getStartOffset() <= inner.getStartOffset() && inner.getEndOffset() <= outer.getEndOffset();
  }

  public boolean containsOffset(int offset) {
    return myStartOffset <= offset && offset <= myEndOffset;
  }

  @Override
  public String toString() {
    return "(" + myStartOffset + "," + myEndOffset + ")";
  }

  public boolean contains(int offset) {
    return myStartOffset <= offset && offset < myEndOffset;
  }

  @NotNull
  @Contract(pure = true)
  public String substring(@NotNull String str) {
    return str.substring(myStartOffset, myEndOffset);
  }

  @NotNull
  public CharSequence subSequence(@NotNull CharSequence str) {
    return str.subSequence(myStartOffset, myEndOffset);
  }

  @NotNull
  public TextRange cutOut(@NotNull TextRange subRange) {
    if (subRange.getStartOffset() > getLength()) {
      throw new IllegalArgumentException("SubRange: " + subRange + "; this=" + this);
    }
    if (subRange.getEndOffset() > getLength()) {
      throw new IllegalArgumentException("SubRange: " + subRange + "; this=" + this);
    }
    assertProperRange(subRange);
    return new TextRange(myStartOffset + subRange.getStartOffset(),
                         Math.min(myEndOffset, myStartOffset + subRange.getEndOffset()));
  }

  @NotNull
  public TextRange shiftRight(int delta) {
    if (delta == 0) return this;
    return new TextRange(myStartOffset + delta, myEndOffset + delta);
  }

  @NotNull
  public TextRange shiftLeft(int delta) {
    if (delta == 0) return this;
    return new TextRange(myStartOffset - delta, myEndOffset - delta);
  }

  @NotNull
  public TextRange grown(int lengthDelta) {
    if (lengthDelta == 0) {
      return this;
    }
    return from(myStartOffset, getLength() + lengthDelta);
  }

  @NotNull
  public static TextRange from(int offset, int length) {
    return create(offset, offset + length);
  }

  @NotNull
  public static TextRange create(int startOffset, int endOffset) {
    return new TextRange(startOffset, endOffset);
  }

  @NotNull
  public static TextRange create(@NotNull Segment segment) {
    return create(segment.getStartOffset(), segment.getEndOffset());
  }

  public static boolean areSegmentsEqual(@NotNull Segment segment1, @NotNull Segment segment2) {
    return segment1.getStartOffset() == segment2.getStartOffset()
           && segment1.getEndOffset() == segment2.getEndOffset();
  }

  @NotNull
  public String replace(@NotNull String original, @NotNull String replacement) {
    String beginning = original.substring(0, getStartOffset());
    String ending = original.substring(getEndOffset());
    return beginning + replacement + ending;
  }

  public boolean intersects(@NotNull TextRange textRange) {
    return intersects((Segment)textRange);
  }

  public boolean intersects(@NotNull Segment textRange) {
    return intersects(textRange.getStartOffset(), textRange.getEndOffset());
  }

  public boolean intersects(int startOffset, int endOffset) {
    return Math.max(myStartOffset, startOffset) <= Math.min(myEndOffset, endOffset);
  }

  public boolean intersectsStrict(@NotNull TextRange textRange) {
    return intersectsStrict(textRange.getStartOffset(), textRange.getEndOffset());
  }

  public boolean intersectsStrict(int startOffset, int endOffset) {
    return Math.max(myStartOffset, startOffset) < Math.min(myEndOffset, endOffset);
  }

  public TextRange intersection(@NotNull TextRange range) {
    if (equals(range)) {
      return this;
    }
    int newStart = Math.max(myStartOffset, range.getStartOffset());
    int newEnd = Math.min(myEndOffset, range.getEndOffset());
    return isProperRange(newStart, newEnd) ? new TextRange(newStart, newEnd) : null;
  }

  public boolean isEmpty() {
    return myStartOffset >= myEndOffset;
  }

  @NotNull
  public TextRange union(@NotNull TextRange textRange) {
    if (equals(textRange)) {
      return this;
    }
    return new TextRange(Math.min(myStartOffset, textRange.getStartOffset()), Math.max(myEndOffset, textRange.getEndOffset()));
  }

  public boolean equalsToRange(int startOffset, int endOffset) {
    return startOffset == myStartOffset && endOffset == myEndOffset;
  }

  @NotNull
  public static TextRange allOf(@NotNull String s) {
    return new TextRange(0, s.length());
  }

  public static void assertProperRange(@NotNull Segment range) throws AssertionError {
    assertProperRange(range, "");
  }

  public static void assertProperRange(@NotNull Segment range, @NotNull Object message) throws AssertionError {
    assertProperRange(range.getStartOffset(), range.getEndOffset(), message);
  }

  public static void assertProperRange(int startOffset, int endOffset, @NotNull Object message) {
    if (!isProperRange(startOffset, endOffset)) {
      throw new IllegalArgumentException("Invalid range specified: (" + startOffset + ", " + endOffset + "); " + message);
    }
  }

  public static boolean isProperRange(int startOffset, int endOffset) {
    return startOffset <= endOffset && startOffset >= 0;
  }

  // methods below intended to work with the alternative TextRange representation as a long value (which logically consists of two int parts for startOffset and endOffset)
  public long toScalarRange() {
    return toScalarRange(getStartOffset(), getEndOffset());
  }

  public static long toScalarRange(int start, int end) {
    return ((long)start << 32) | end;
  }

  public static long union(long range1, long range2) {
    if (range1 == range2) return range1;
    int start = Math.min(startOffset(range1), startOffset(range2));
    int end = Math.max(endOffset(range1), endOffset(range2));
    return toScalarRange(start, end);
  }

  public static int endOffset(long range) {
    return (int)range & Integer.MAX_VALUE;
  }

  public static int startOffset(long range) {
    return (int)(range >>> 32);
  }

  public static boolean contains(long outerRange, long innerRange) {
    return containsRange(outerRange, startOffset(innerRange), endOffset(innerRange));
  }

  public static boolean containsRange(long outerRange, int innerRangeStartOffset, int innerRangeEndOffset) {
    return startOffset(outerRange) <= innerRangeStartOffset && innerRangeEndOffset <= endOffset(outerRange);
  }
  public boolean intersects(long range) {
    return intersects(startOffset(range), endOffset(range));
  }

  @NotNull
  public static TextRange create(long range) {
    return create(startOffset(range), endOffset(range));
  }
  public boolean equalsToRange(long range) {
    return getStartOffset() == startOffset(range) && getEndOffset() == endOffset(range);
  }
}
