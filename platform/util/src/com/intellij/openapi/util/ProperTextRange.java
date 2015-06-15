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

import org.jetbrains.annotations.NotNull;

/**
 * Text range which asserts its non-negative startOffset and length
 */
public class ProperTextRange extends TextRange {
  public ProperTextRange(int startOffset, int endOffset) {
    super(startOffset, endOffset);
  }

  public ProperTextRange(@NotNull TextRange range) {
    this(range.getStartOffset(), range.getEndOffset());
  }

  @NotNull
  @Override
  public ProperTextRange cutOut(@NotNull TextRange subRange) {
    assert subRange.getStartOffset() <= getLength() : subRange + "; this="+this;
    assert subRange.getEndOffset() <= getLength() : subRange + "; this="+this;
    return new ProperTextRange(getStartOffset() + subRange.getStartOffset(), Math.min(getEndOffset(), getStartOffset() + subRange.getEndOffset()));
  }

  @NotNull
  @Override
  public ProperTextRange shiftRight(int delta) {
    if (delta == 0) return this;
    return new ProperTextRange(getStartOffset() + delta, getEndOffset() + delta);
  }

  @NotNull
  @Override
  public ProperTextRange grown(int lengthDelta) {
    if (lengthDelta == 0) return this;
    return new ProperTextRange(getStartOffset(), getEndOffset() + lengthDelta);
  }

  @Override
  public ProperTextRange intersection(@NotNull TextRange textRange) {
    assertProperRange(textRange);
    TextRange range = super.intersection(textRange);
    if (range == null) return null;
    return new ProperTextRange(range);
  }

  @NotNull
  @Override
  public ProperTextRange union(@NotNull TextRange textRange) {
    assertProperRange(textRange);
    TextRange range = super.union(textRange);
    return new ProperTextRange(range);
  }

  @NotNull
  public static ProperTextRange create(@NotNull Segment segment) {
    return new ProperTextRange(segment.getStartOffset(), segment.getEndOffset());
  }

  @NotNull
  public static ProperTextRange create(int startOffset, int endOffset) {
    return new ProperTextRange(startOffset, endOffset);
  }

  @NotNull
  public static ProperTextRange from(int offset, int length) {
    return new ProperTextRange(offset, offset + length);
  }
}
