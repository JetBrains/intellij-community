// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @Override
  public @NotNull ProperTextRange cutOut(@NotNull TextRange subRange) {
    assert subRange.getStartOffset() <= getLength() : subRange + "; this="+this;
    assert subRange.getEndOffset() <= getLength() : subRange + "; this="+this;
    return new ProperTextRange(getStartOffset() + subRange.getStartOffset(), Math.min(getEndOffset(), getStartOffset() + subRange.getEndOffset()));
  }

  @Override
  public @NotNull ProperTextRange shiftRight(int delta) {
    if (delta == 0) return this;
    return new ProperTextRange(getStartOffset() + delta, getEndOffset() + delta);
  }

  @Override
  public @NotNull ProperTextRange grown(int lengthDelta) {
    if (lengthDelta == 0) return this;
    return new ProperTextRange(getStartOffset(), getEndOffset() + lengthDelta);
  }

  @Override
  public ProperTextRange intersection(@NotNull TextRange range) {
    assertProperRange(range);

    int newStart = Math.max(getStartOffset(), range.getStartOffset());
    int newEnd = Math.min(getEndOffset(), range.getEndOffset());
    return isProperRange(newStart, newEnd) ? new ProperTextRange(newStart, newEnd) : null;
  }

  @Override
  public @NotNull ProperTextRange union(@NotNull TextRange textRange) {
    assertProperRange(textRange);
    TextRange range = super.union(textRange);
    return new ProperTextRange(range);
  }

  public static @NotNull ProperTextRange create(@NotNull Segment segment) {
    return new ProperTextRange(segment.getStartOffset(), segment.getEndOffset());
  }

  public static @NotNull ProperTextRange create(int startOffset, int endOffset) {
    return new ProperTextRange(startOffset, endOffset);
  }

  public static @NotNull ProperTextRange from(int offset, int length) {
    return new ProperTextRange(offset, offset + length);
  }
}
