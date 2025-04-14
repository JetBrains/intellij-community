// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  @Contract(pure = true)
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

  /**
   * Checks if the specified text range is fully contained within this TextRange.
   *
   * @param range the text range to check for containment
   * @return {@code true} if the specified text range is contained within this object, 
   *         {@code false} otherwise
   */
  @Contract(pure = true)
  public boolean contains(@NotNull TextRange range) {
    return contains((Segment)range);
  }

  /**
   * Checks if the given segment is fully contained within this TextRange.
   *
   * @param segment the segment to be checked for containment
   * @return true if the given range is contained within this segment, false otherwise
   */
  @Contract(pure = true)
  public boolean contains(@NotNull Segment segment) {
    return containsRange(segment.getStartOffset(), segment.getEndOffset());
  }

  /**
   * Checks if the given range is fully contained within this TextRange.
   *
   * @param startOffset the start offset of the range to check
   * @param endOffset the end offset of the range to check
   * @return true if the given range is contained within this TextRange, false otherwise
   */
  @Contract(pure = true)
  public boolean containsRange(int startOffset, int endOffset) {
    return getStartOffset() <= startOffset && endOffset <= getEndOffset();
  }

  @Contract(pure = true)
  public static boolean containsRange(@NotNull Segment outer, @NotNull Segment inner) {
    return outer.getStartOffset() <= inner.getStartOffset() && inner.getEndOffset() <= outer.getEndOffset();
  }

  /**
   * Checks if the given offset is contained within the range 
   * (unlike {@link #contains(int)}, offset at the end of the range is considered to be inside).
   *
   * @param offset the offset to check
   * @return true if the given offset is within the range, false otherwise
   * @see #contains(int) 
   */
  @Contract(pure = true)
  public boolean containsOffset(int offset) {
    return myStartOffset <= offset && offset <= myEndOffset;
  }

  @Override
  public String toString() {
    return "(" + myStartOffset + "," + myEndOffset + ")";
  }

  /**
   * Checks if the given offset is contained within the range 
   * (unlike {@link #containsOffset(int)}, offset at the end of the range is considered to be outside).
   *
   * @param offset the offset to check
   * @return true if the given offset is within the range, false otherwise
   * @see #containsOffset(int) 
   */
  @Contract(pure = true)
  public boolean contains(int offset) {
    return myStartOffset <= offset && offset < myEndOffset;
  }

  /**
   * Returns a substring of the given string that is covered by this TextRange.
   *
   * @param str the string from which the substring will be extracted
   * @return the substring
   */
  @Contract(pure = true)
  public @NotNull String substring(@NotNull String str) {
    return str.substring(myStartOffset, myEndOffset);
  }

  @Contract(pure = true)
  public @NotNull CharSequence subSequence(@NotNull CharSequence str) {
    return str.subSequence(myStartOffset, myEndOffset);
  }

  @Contract(pure = true)
  public @NotNull TextRange cutOut(@NotNull TextRange subRange) {
    assertProperRange(subRange);
    if (subRange.getEndOffset() > getLength()) {
      throw new IllegalArgumentException("SubRange: " + subRange + "; this=" + this);
    }
    return new TextRange(myStartOffset + subRange.getStartOffset(),
                         Math.min(myEndOffset, myStartOffset + subRange.getEndOffset()));
  }

  @Contract(pure = true)
  public @NotNull TextRange shiftRight(int delta) {
    if (delta == 0) return this;
    return new TextRange(myStartOffset + delta, myEndOffset + delta);
  }

  @Contract(pure = true)
  public @NotNull TextRange shiftLeft(int delta) {
    if (delta == 0) return this;
    return new TextRange(myStartOffset - delta, myEndOffset - delta);
  }

  @Contract(pure = true)
  public @NotNull TextRange grown(int lengthDelta) {
    if (lengthDelta == 0) {
      return this;
    }
    return from(myStartOffset, getLength() + lengthDelta);
  }

  @Contract(pure = true)
  public static @NotNull TextRange from(int offset, int length) {
    return create(offset, offset + length);
  }

  @Contract(pure = true)
  public static @NotNull TextRange create(int startOffset, int endOffset) {
    return new TextRange(startOffset, endOffset);
  }

  @Contract(pure = true)
  public static @NotNull TextRange create(@NotNull Segment segment) {
    return create(segment.getStartOffset(), segment.getEndOffset());
  }

  @Contract(pure = true)
  public static boolean areSegmentsEqual(@NotNull Segment segment1, @NotNull Segment segment2) {
    return segment1.getStartOffset() == segment2.getStartOffset()
           && segment1.getEndOffset() == segment2.getEndOffset();
  }

  @Contract(pure = true)
  public @NotNull String replace(@NotNull String original, @NotNull String replacement) {
    String beginning = original.substring(0, getStartOffset());
    String ending = original.substring(getEndOffset());
    return beginning + replacement + ending;
  }

  @Contract(pure = true)
  public boolean intersects(@NotNull TextRange textRange) {
    return intersects((Segment)textRange);
  }

  @Contract(pure = true)
  public boolean intersects(@NotNull Segment textRange) {
    return intersects(textRange.getStartOffset(), textRange.getEndOffset());
  }

  @Contract(pure = true)
  public boolean intersects(int startOffset, int endOffset) {
    return Math.max(myStartOffset, startOffset) <= Math.min(myEndOffset, endOffset);
  }

  @Contract(pure = true)
  public boolean intersectsStrict(@NotNull TextRange textRange) {
    return intersectsStrict(textRange.getStartOffset(), textRange.getEndOffset());
  }

  @Contract(pure = true)
  public boolean intersectsStrict(int startOffset, int endOffset) {
    return Math.max(myStartOffset, startOffset) < Math.min(myEndOffset, endOffset);
  }

  @Contract(pure = true)
  public TextRange intersection(@NotNull TextRange range) {
    if (equals(range)) {
      return this;
    }
    int newStart = Math.max(myStartOffset, range.getStartOffset());
    int newEnd = Math.min(myEndOffset, range.getEndOffset());
    return isProperRange(newStart, newEnd) ? new TextRange(newStart, newEnd) : null;
  }

  @Contract(pure = true)
  public boolean isEmpty() {
    return myStartOffset >= myEndOffset;
  }

  @Contract(pure = true)
  public @NotNull TextRange union(@NotNull TextRange textRange) {
    if (equals(textRange)) {
      return this;
    }
    return new TextRange(Math.min(myStartOffset, textRange.getStartOffset()), Math.max(myEndOffset, textRange.getEndOffset()));
  }

  @Contract(pure = true)
  public boolean equalsToRange(int startOffset, int endOffset) {
    return startOffset == myStartOffset && endOffset == myEndOffset;
  }

  @Contract(pure = true)
  public static @NotNull TextRange allOf(@NotNull String s) {
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

  /**
   * @return true if {@link ProperTextRange#create} will work on this range
   */
  public boolean isProperRange() {
    return isProperRange(getStartOffset(), getEndOffset());
  }
}
