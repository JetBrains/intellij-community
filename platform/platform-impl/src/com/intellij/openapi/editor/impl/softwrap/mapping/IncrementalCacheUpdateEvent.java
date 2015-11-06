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
package com.intellij.openapi.editor.impl.softwrap.mapping;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
import org.jetbrains.annotations.NotNull;

/**
 * Encapsulates information about incremental soft wraps cache update.
 * 
 * @author Denis Zhdanov
 * @since 11/17/10 9:33 AM
 */
public class IncrementalCacheUpdateEvent {
  private static Logger LOG = Logger.getInstance(IncrementalCacheUpdateEvent.class);
  
  private final int myStartOffset;
  private final int myMandatoryEndOffset;
  private int myActualEndOffset = -1;
  
  private final int myLengthDiff;
  
  @NotNull
  private final LogicalPosition myStartLogicalPosition;
  @NotNull 
  private final VisualPosition myStartVisualPosition;
  private final int myOldEndLogicalLine;
  private int myNewEndLogicalLine = -1;

  /**
   * Creates new <code>IncrementalCacheUpdateEvent</code> object on the basis on the given event object that describes
   * document change that caused cache update.
   * <p/>
   * This constructor is assumed to be used <b>before</b> the document change, {@link #updateAfterDocumentChange(Document)}
   * should be called <b>'after'</b> document change to complete object creation.
   * 
   * @param event   object that describes document change that caused cache update
   */
  IncrementalCacheUpdateEvent(@NotNull DocumentEvent event, @NotNull CachingSoftWrapDataMapper mapper, @NotNull EditorImpl editor) {
    this(event.getOffset(), event.getOffset() + event.getOldLength(), event.getOffset() + event.getNewLength(), mapper, editor);
  }

  /**
   * Creates new <code>IncrementalCacheUpdateEvent</code> object for the event not changing document length
   * (like expansion of folded region).
   */
  IncrementalCacheUpdateEvent(int startOffset, int endOffset, @NotNull CachingSoftWrapDataMapper mapper, @NotNull EditorImpl editor) {
    this(startOffset, endOffset, endOffset, mapper, editor);
    myNewEndLogicalLine = myOldEndLogicalLine;
  }

  /**
   * Creates new <code>IncrementalCacheUpdateEvent</code> object that is configured to perform whole reparse of the given
   * document.
   * 
   * @param document    target document to reparse
   */
  IncrementalCacheUpdateEvent(@NotNull Document document) {
    myStartOffset = 0;
    myMandatoryEndOffset = document.getTextLength();
    myLengthDiff = 0;
    myStartLogicalPosition = new LogicalPosition(0, 0, 0, 0, 0, 0, 0);
    myStartVisualPosition = new VisualPosition(0, 0);
    myOldEndLogicalLine = myNewEndLogicalLine = Math.max(0, document.getLineCount() - 1);
  }
  
  private IncrementalCacheUpdateEvent(int startOffset, int oldEndOffset, int newEndOffset, 
                                      @NotNull CachingSoftWrapDataMapper mapper, @NotNull EditorImpl editor) {
    if (editor.myUseNewRendering) {
      VisualLineInfo info = getVisualLineInfo(editor, startOffset, false);
      if (info.startsWithSoftWrap) {
        info = getVisualLineInfo(editor, info.startOffset, true);
      }
      myStartOffset = info.startOffset;
      myStartLogicalPosition = editor.offsetToLogicalPosition(myStartOffset);
      myStartVisualPosition = new VisualPosition(info.visualLine, 0);
    }
    else {
      myStartOffset = mapper.getPreviousVisualLineStartOffset(startOffset);
      myStartLogicalPosition = mapper.offsetToLogicalPosition(myStartOffset);
      LOG.assertTrue(myStartLogicalPosition.visualPositionAware);
      myStartVisualPosition = myStartLogicalPosition.toVisualPosition();
    }
    myMandatoryEndOffset = newEndOffset;
    myLengthDiff = newEndOffset - oldEndOffset;
    myOldEndLogicalLine = editor.getDocument().getLineNumber(oldEndOffset);
  }


  private static VisualLineInfo getVisualLineInfo(@NotNull EditorImpl editor, int offset, boolean beforeSoftWrap) {
    Document document = editor.getDocument();
    int textLength = document.getTextLength();
    if (offset <= 0 || textLength == 0) return new VisualLineInfo(0, 0, false);
    offset = Math.min(offset, textLength);

    int startOffset = EditorUtil.getNotFoldedLineStartOffset(editor, offset);

    SoftWrapModelImpl softWrapModel = editor.getSoftWrapModel();
    int wrapIndex = softWrapModel.getSoftWrapIndex(offset);
    int prevSoftWrapIndex = wrapIndex < 0 ? (- wrapIndex - 2) : wrapIndex - (beforeSoftWrap ? 1 : 0);
    SoftWrap prevSoftWrap = prevSoftWrapIndex < 0 ? null : softWrapModel.getRegisteredSoftWraps().get(prevSoftWrapIndex);
    
    int visualLine = document.getLineNumber(offset) - editor.getFoldingModel().getFoldedLinesCountBefore(offset) + (prevSoftWrapIndex + 1);
    int visualLineStartOffset = prevSoftWrap == null ? startOffset : Math.max(startOffset, prevSoftWrap.getStart());
    return new VisualLineInfo(visualLine, 
                              visualLineStartOffset, 
                              prevSoftWrap != null && prevSoftWrap.getStart() == visualLineStartOffset);
  }
  
  private static class VisualLineInfo {
    private final int visualLine;
    private final int startOffset;
    private final boolean startsWithSoftWrap;

    private VisualLineInfo(int visualLine, int startOffset, boolean wrap) {
      this.visualLine = visualLine;
      this.startOffset = startOffset;
      startsWithSoftWrap = wrap;
    }
  }

  public void updateAfterDocumentChange(@NotNull Document document) {
    myNewEndLogicalLine = document.getLineNumber(myMandatoryEndOffset);
  }

  /**
   * Returns offset, from which soft wrap recalculation should start
   */
  public int getStartOffset() {
    return myStartOffset;
  }

  /**
   * Returns logical position, from which soft wrap recalculation should start
   */
  @NotNull
  public LogicalPosition getStartLogicalPosition() {
    return myStartLogicalPosition;
  }

  /**
   * Returns visual position, from which soft wrap recalculation should start
   */
  @NotNull
  public VisualPosition getStartVisualPosition() {
    return myStartVisualPosition;
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

  void setActualEndOffset(int actualEndOffset) {
    myActualEndOffset = actualEndOffset;
  }

  /**
   * Returns change in document length for the event causing soft wrap recalculation.
   */
  public int getLengthDiff() {
    return myLengthDiff;
  }

  /**
   * Returns change in document line count for the event causing soft wrap recalculation.
   */
  public int getLogicalLinesDiff() {
    return myNewEndLogicalLine - myOldEndLogicalLine;
  }

  /**
   * Returns line number for initial change's starting point 
   */
  public int getStartLogicalLine() {
    return myStartLogicalPosition.line;
  }

  /**
   * Returns line number for initial change's ending point 
   */
  public int getOldEndLogicalLine() {
    return myOldEndLogicalLine;
  }

  @Override
  public String toString() {
    return "startOffset=" + myStartOffset +
           ", mandatoryEndOffset=" + myMandatoryEndOffset +
           ", actualEndOffset=" + myActualEndOffset +
           ", lengthDiff=" + myLengthDiff +
           ", startLogicalPosition=" + myStartLogicalPosition +
           ", oldEndLogicalLine=" + myOldEndLogicalLine +
           ", newEndLogicalLine=" + myNewEndLogicalLine;
  }
}
