// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.softwrap.mapping;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.SoftWrap;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
import org.jetbrains.annotations.NotNull;

/**
 * Encapsulates information about incremental soft wraps cache update.
 *
 * @author Denis Zhdanov
 */
public class IncrementalCacheUpdateEvent {
  private final int myStartOffset;
  private final int myMandatoryEndOffset;
  private int myActualEndOffset = -1;

  private final int myLengthDiff;

  /**
   * Creates new {@code IncrementalCacheUpdateEvent} object on the basis on the given event object that describes
   * document change that caused cache update.
   * <p/>
   * This constructor is assumed to be used <b>before</b> the document change.
   *
   * @param event   object that describes document change that caused cache update
   */
  IncrementalCacheUpdateEvent(@NotNull DocumentEvent event, @NotNull EditorImpl editor) {
    this(event.getOffset(), event.getOffset() + event.getOldLength(), event.getOffset() + event.getNewLength(), editor);
  }

  /**
   * Creates new {@code IncrementalCacheUpdateEvent} object for the event not changing document length
   * (like expansion of folded region).
   */
  IncrementalCacheUpdateEvent(int startOffset, int endOffset, @NotNull EditorImpl editor) {
    this(startOffset, endOffset, endOffset, editor);
  }

  /**
   * Creates new {@code IncrementalCacheUpdateEvent} object that is configured to perform whole reparse of the given
   * document.
   *
   * @param document    target document to reparse
   */
  IncrementalCacheUpdateEvent(@NotNull Document document) {
    myStartOffset = 0;
    myMandatoryEndOffset = document.getTextLength();
    myLengthDiff = 0;
  }

  private IncrementalCacheUpdateEvent(int startOffset, int oldEndOffset, int newEndOffset, @NotNull EditorImpl editor) {
    VisualLineInfo info = getVisualLineInfo(editor, startOffset, false);
    if (info.startsWithSoftWrap) {
      info = getVisualLineInfo(editor, info.startOffset, true);
    }
    myStartOffset = info.startOffset;
    myMandatoryEndOffset = newEndOffset;
    myLengthDiff = newEndOffset - oldEndOffset;
  }


  private static VisualLineInfo getVisualLineInfo(@NotNull EditorImpl editor, int offset, boolean beforeSoftWrap) {
    Document document = editor.getDocument();
    int textLength = document.getTextLength();
    if (offset <= 0 || textLength == 0) return new VisualLineInfo(0, false);
    offset = Math.min(offset, textLength);

    int startOffset = EditorUtil.getNotFoldedLineStartOffset(editor, offset);

    SoftWrapModelImpl softWrapModel = editor.getSoftWrapModel();
    int wrapIndex = softWrapModel.getSoftWrapIndex(offset);
    int prevSoftWrapIndex = wrapIndex < 0 ? - wrapIndex - 2 : wrapIndex - (beforeSoftWrap ? 1 : 0);
    SoftWrap prevSoftWrap = prevSoftWrapIndex < 0 ? null : softWrapModel.getRegisteredSoftWraps().get(prevSoftWrapIndex);

    int visualLineStartOffset = prevSoftWrap == null ? startOffset : Math.max(startOffset, prevSoftWrap.getStart());
    return new VisualLineInfo(visualLineStartOffset, prevSoftWrap != null && prevSoftWrap.getStart() == visualLineStartOffset);
  }

  private static final class VisualLineInfo {
    private final int startOffset;
    private final boolean startsWithSoftWrap;

    private VisualLineInfo(int startOffset, boolean wrap) {
      this.startOffset = startOffset;
      startsWithSoftWrap = wrap;
    }
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
