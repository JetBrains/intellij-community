/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

public class TextRange implements Segment, Serializable {
  private static final Logger LOG = Logger.getInstance(TextRange.class);
  private static final long serialVersionUID = -670091356599757430L;
  public static final TextRange EMPTY_RANGE = new TextRange(0,0);
  private final int myStartOffset;
  private final int myEndOffset;

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
  public String substring(@NotNull String str) {
    try {
      return str.substring(myStartOffset, myEndOffset);
    }
    catch (StringIndexOutOfBoundsException e) {
      throw new StringIndexOutOfBoundsException("Can't extract " + this + " range from " + str);
    }
  }

  @NotNull
  public CharSequence subSequence(@NotNull CharSequence str) {
    try {
      return str.subSequence(myStartOffset, myEndOffset);
    }
    catch (IndexOutOfBoundsException e) {
      throw new IndexOutOfBoundsException("Can't extract " + this + " range from " + str);
    }
  }

  @NotNull
  public TextRange cutOut(@NotNull TextRange subRange) {
    assert subRange.getStartOffset() <= getLength() : "SubRange: " + subRange + "; this=" + this;
    assert subRange.getEndOffset() <= getLength() : "SubRange: " + subRange + "; this=" + this;
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
    try {
      String beginning = original.substring(0, getStartOffset());
      String ending = original.substring(getEndOffset(), original.length());
      return beginning + replacement + ending;
    }
    catch (StringIndexOutOfBoundsException e) {
      throw new StringIndexOutOfBoundsException("Can't replace " + this + " range from '" + original + "' with '" + replacement + "'");
    }
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

  @Nullable
  public TextRange intersection(@NotNull TextRange range) {
    int newStart = Math.max(myStartOffset, range.getStartOffset());
    int newEnd = Math.min(myEndOffset, range.getEndOffset());
    return isProperRange(newStart, newEnd) ? new TextRange(newStart, newEnd) : null;
  }

  public boolean isEmpty() {
    return myStartOffset >= myEndOffset;
  }

  @NotNull
  public TextRange union(@NotNull TextRange textRange) {
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
      LOG.error("Invalid range specified: (" + startOffset + ", " + endOffset + "); " + message);
    }
  }

  private static boolean isProperRange(int startOffset, int endOffset) {
    return startOffset <= endOffset && startOffset >= 0;
  }
}
