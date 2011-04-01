/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TextRange implements Segment{
  public static final TextRange EMPTY_RANGE = new TextRange(0,0);
  private final int myStartOffset;
  private final int myEndOffset;

  public TextRange(int startOffset, int endOffset) {
    myStartOffset = startOffset;
    myEndOffset = endOffset;
  }

  public final int getStartOffset() {
    return myStartOffset;
  }

  public final int getEndOffset() {
    return myEndOffset;
  }

  public final int getLength() {
    return myEndOffset - myStartOffset;
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof TextRange)) return false;
    TextRange range = (TextRange)obj;
    return myStartOffset == range.myStartOffset && myEndOffset == range.myEndOffset;
  }

  public int hashCode() {
    return myStartOffset + myEndOffset;
  }

  public boolean contains(@NotNull TextRange range) {
    return containsRange(range.getStartOffset(), range.getEndOffset());
  }

  public boolean containsRange(int startOffset, int endOffset) {
    return myStartOffset <= startOffset && myEndOffset >= endOffset;
  }

  public boolean containsOffset(int offset) {
    return myStartOffset <= offset && offset <= myEndOffset;
  }

  public String toString() {
    return "(" + myStartOffset + "," + myEndOffset + ")";
  }

  public boolean contains(int offset) {
    return myStartOffset <= offset && offset < myEndOffset;
  }

  @NotNull
  public String substring(@NotNull String str) {
    return str.substring(myStartOffset, myEndOffset);
  }

  @NotNull
  public TextRange cutOut(@NotNull TextRange subRange) {
    assert subRange.getStartOffset() <= getLength() : subRange + "; this="+this;
    assert subRange.getEndOffset() <= getLength() : subRange + "; this="+this;
    return new TextRange(myStartOffset + subRange.getStartOffset(), Math.min(myEndOffset, myStartOffset + subRange.getEndOffset()));
  }

  @NotNull
  public TextRange shiftRight(int delta) {
    if (delta == 0) return this;
    return new TextRange(myStartOffset + delta, myEndOffset + delta);
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

  @NotNull
  public String replace(@NotNull String original, @NotNull String replacement) {
    String beginning = original.substring(0, getStartOffset());
    String ending = original.substring(getEndOffset(), original.length());
    return beginning + replacement + ending;
  }

  public boolean intersects(@NotNull TextRange textRange) {
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
    if (!intersects(range)) return null;
    return new TextRange(Math.max(myStartOffset, range.getStartOffset()), Math.min(myEndOffset, range.getEndOffset()));
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
}
