// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.softwrap.mapping;

import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;

/**
 * Encapsulates information about incremental soft wraps cache update.
 */
public final class IncrementalCacheUpdateEvent {
  private final int myStartOffset;
  private final int myMandatoryEndOffset;
  private int myActualEndOffset = -1;

  private final int myLengthDiff;

  IncrementalCacheUpdateEvent(int startOffset, int mandatoryEndOffset, int lengthDiff) {
    myStartOffset = startOffset;
    myMandatoryEndOffset = mandatoryEndOffset;
    myLengthDiff = lengthDiff;
  }

  /**
   * Creates new {@code IncrementalCacheUpdateEvent} object that is configured to perform whole reparse of the given
   * document.
   *
   * @param document    target document to reparse
   */
  static IncrementalCacheUpdateEvent forWholeDocument(@NotNull Document document) {
    return new IncrementalCacheUpdateEvent(0, document.getTextLength(), 0);
  }

  /**
   * Returns offset, from which soft wrap recalculation should start
   */
  public int getStartOffset() {
    return myStartOffset;
  }

  /**
   * Returns offset, till which soft wrap recalculation should proceed
   */
  public int getMandatoryEndOffset() {
    return myMandatoryEndOffset;
  }

  /**
   * Returns offset, till which soft wrap recalculation actually was performed. It can be larger that the value returned by
   * {@link #getMandatoryEndOffset()}.
   */
  public int getActualEndOffset() {
    return myActualEndOffset;
  }

  public void setActualEndOffset(int actualEndOffset) {
    myActualEndOffset = actualEndOffset;
  }

  /**
   * Returns change in document length for the event causing soft wrap recalculation.
   */
  public int getLengthDiff() {
    return myLengthDiff;
  }

  @Override
  public String toString() {
    return "startOffset=" + myStartOffset +
           ", mandatoryEndOffset=" + myMandatoryEndOffset +
           ", actualEndOffset=" + myActualEndOffset +
           ", lengthDiff=" + myLengthDiff;
  }
}
