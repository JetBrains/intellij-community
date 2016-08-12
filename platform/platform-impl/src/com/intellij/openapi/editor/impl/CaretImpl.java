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
package com.intellij.openapi.editor.impl;

import com.intellij.diagnostic.Dumpable;
import com.intellij.diagnostic.LogMessageEx;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapHelper;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.diff.FilesTooBigForDiffException;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.EmptyClipboardOwner;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.List;

public class CaretImpl extends UserDataHolderBase implements Caret, Dumpable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.CaretImpl");

  private final EditorImpl myEditor;
  private boolean isValid = true;

  private LogicalPosition myLogicalCaret;
  private VerticalInfo myCaretInfo;
  private VisualPosition myVisibleCaret;
  private int myOffset;
  private int myVirtualSpaceOffset;
  private int myVisualLineStart;
  private int myVisualLineEnd;
  private RangeMarker savedBeforeBulkCaretMarker;
  private boolean mySkipChangeRequests;
  /**
   * Initial horizontal caret position during vertical navigation.
   * Similar to {@link #myDesiredX}, but represents logical caret position (<code>getLogicalPosition().column</code>) rather than visual.
   */
  private int myLastColumnNumber = 0;
  private int myDesiredSelectionStartColumn = -1;
  private int myDesiredSelectionEndColumn = -1;
  /**
   * We check that caret is located at the target offset at the end of {@link #moveToOffset(int, boolean)} method. However,
   * it's possible that the following situation occurs:
   * <p/>
   * <pre>
   * <ol>
   *   <li>Some client subscribes to caret change events;</li>
   *   <li>{@link #moveToLogicalPosition(LogicalPosition)} is called;</li>
   *   <li>Caret position is changed during {@link #moveToLogicalPosition(LogicalPosition)} processing;</li>
   *   <li>The client receives caret position change event and adjusts the position;</li>
   *   <li>{@link #moveToLogicalPosition(LogicalPosition)} processing is finished;</li>
   *   <li>{@link #moveToLogicalPosition(LogicalPosition)} reports an error because the caret is not located at the target offset;</li>
   * </ol>
   * </pre>
   * <p/>
   * This field serves as a flag that reports unexpected caret position change requests nested from {@link #moveToOffset(int, boolean)}.
   */
  private boolean myReportCaretMoves;
  /**
   * This field holds initial horizontal caret position during vertical navigation. It's used to determine target position when
   * moving to the new line. It is stored in pixels, not in columns, to account for non-monospaced fonts as well.
   * <p/>
   * Negative value means no coordinate should be preserved.
   */
  private int myDesiredX = -1;

  private volatile RangeMarker mySelectionMarker;
  private volatile VisualPosition myRangeMarkerStartPosition;
  private volatile VisualPosition myRangeMarkerEndPosition;
  private volatile boolean myRangeMarkerEndPositionIsLead;

  private int startBefore;
  private int endBefore;
  boolean myUnknownDirection;
  // offsets of selection start/end position relative to end of line - can be non-zero in column selection mode
  // these are non-negative values, myStartVirtualOffset is always less or equal to myEndVirtualOffset
  private int myStartVirtualOffset;
  private int myEndVirtualOffset;

  CaretImpl(EditorImpl editor) {
    myEditor = editor;

    myLogicalCaret = new LogicalPosition(0, 0);
    myVisibleCaret = new VisualPosition(0, 0);
    myCaretInfo = new VerticalInfo(0, 0);
    myOffset = 0;
    myVisualLineStart = 0;
    Document doc = myEditor.getDocument();
    myVisualLineEnd = doc.getLineCount() > 1 ? doc.getLineStartOffset(1) : doc.getLineCount() == 0 ? 0 : doc.getLineEndOffset(0);
  }

  void onBulkDocumentUpdateStarted() {
    Document doc = myEditor.getDocument();
    if (myOffset > doc.getTextLength() || savedBeforeBulkCaretMarker != null) return;
    savedBeforeBulkCaretMarker = doc.createRangeMarker(myOffset, myOffset);
    beforeDocumentChange();
  }

  void onBulkDocumentUpdateFinished() {
    Document doc = myEditor.getDocument();
    if (myEditor.getCaretModel().myIsInUpdate) return;
    LOG.assertTrue(!myReportCaretMoves);

    if (savedBeforeBulkCaretMarker != null) {
      if(savedBeforeBulkCaretMarker.isValid()) {
        if(savedBeforeBulkCaretMarker.getStartOffset() != myOffset) {
          moveToOffset(savedBeforeBulkCaretMarker.getStartOffset());
        }
      } else if (myOffset > doc.getTextLength()) {
        moveToOffset(doc.getTextLength());
      }
      releaseBulkCaretMarker();
    }
    documentChanged();
  }

  public void beforeDocumentChange() {
    RangeMarker marker = mySelectionMarker;
    if (marker != null && marker.isValid()) {
      startBefore = marker.getStartOffset();
      endBefore = marker.getEndOffset();
    }
  }

  public void documentChanged() {
    RangeMarker marker = mySelectionMarker;
    if (marker != null) {
      int endAfter;
      int startAfter;
      if (marker.isValid()) {
        startAfter = marker.getStartOffset();
        endAfter = marker.getEndOffset();
        if (myEndVirtualOffset > 0 && (!isVirtualSelectionEnabled()
                                       || !EditorUtil.isAtLineEnd(myEditor, endAfter)
                                       || myEditor.getDocument().getLineNumber(startAfter) != myEditor.getDocument().getLineNumber(endAfter))) {
          myStartVirtualOffset = 0;
          myEndVirtualOffset = 0;
        }
      }
      else {
        startAfter = endAfter = getOffset();
        marker.dispose();
        myStartVirtualOffset = 0;
        myEndVirtualOffset = 0;
        mySelectionMarker = null;
      }

      if (startBefore != startAfter || endBefore != endAfter) {
        myEditor.getSelectionModel().fireSelectionChanged(startBefore, endBefore, startAfter, endAfter);
      }
    }
  }

  @Override
  public void moveToOffset(int offset) {
    moveToOffset(offset, false);
  }

  @Override
  public void moveToOffset(final int offset, final boolean locateBeforeSoftWrap) {
    assertIsDispatchThread();
    validateCallContext();
    if (mySkipChangeRequests) {
      return;
    }
    myEditor.getCaretModel().doWithCaretMerging(() -> {
      final LogicalPosition logicalPosition = myEditor.offsetToLogicalPosition(offset);
      CaretEvent event = moveToLogicalPosition(logicalPosition, locateBeforeSoftWrap, null, false);
      final LogicalPosition positionByOffsetAfterMove = myEditor.offsetToLogicalPosition(myOffset);
      if (!positionByOffsetAfterMove.equals(logicalPosition)) {
        StringBuilder debugBuffer = new StringBuilder();
        moveToLogicalPosition(logicalPosition, locateBeforeSoftWrap, debugBuffer, true);
        int textStart = Math.max(0, Math.min(offset, myOffset) - 1);
        final DocumentEx document = myEditor.getDocument();
        int textEnd = Math.min(document.getTextLength() - 1, Math.max(offset, myOffset) + 1);
        CharSequence text = document.getCharsSequence().subSequence(textStart, textEnd);
        int inverseOffset = myEditor.logicalPositionToOffset(logicalPosition);
        LogMessageEx.error(
          LOG, "caret moved to wrong offset. Please submit a dedicated ticket and attach current editor's text to it.",
          "Requested: offset=" + offset + ", logical position='" + logicalPosition + "' but actual: offset=" +
          myOffset + ", logical position='" + myLogicalCaret + "' (" + positionByOffsetAfterMove + "). " + myEditor.dumpState() +
          "\ninterested text [" + textStart + ";" + textEnd + "): '" + text + "'\n debug trace: " + debugBuffer +
          "\nLogical position -> offset ('" + logicalPosition + "'->'" + inverseOffset + "')"
        );
      }
      if (event != null) {
        myEditor.getCaretModel().fireCaretPositionChanged(event);
        EditorActionUtil.selectNonexpandableFold(myEditor);
      }
    });
  }

  @NotNull
  @Override
  public CaretModel getCaretModel() {
    return myEditor.getCaretModel();
  }

  @Override
  public boolean isValid() {
    return isValid;
  }

  @Override
  public void moveCaretRelatively(final int columnShift, final int lineShift, final boolean withSelection, final boolean scrollToCaret) {
    assertIsDispatchThread();
    if (mySkipChangeRequests) {
      return;
    }
    if (myReportCaretMoves) {
      LogMessageEx.error(LOG, "Unexpected caret move request");
    }
    if (!myEditor.isStickySelection() && !myEditor.getCaretModel().isDocumentChanged) {
      CopyPasteManager.getInstance().stopKillRings();
    }
    myEditor.getCaretModel().doWithCaretMerging(() -> {
      int oldOffset = myOffset;
      final int leadSelectionOffset = getLeadSelectionOffset();
      final VisualPosition leadSelectionPosition = getLeadSelectionPosition();
      EditorSettings editorSettings = myEditor.getSettings();
      VisualPosition visualCaret = getVisualPosition();

      int lastColumnNumber = myLastColumnNumber;
      int desiredX = myDesiredX;
      if (columnShift == 0) {
        if (myDesiredX < 0) {
          desiredX = getCurrentX();
        }
      }
      else {
        myDesiredX = desiredX = -1;
      }

      int newLineNumber = visualCaret.line + lineShift;
      int newColumnNumber = visualCaret.column + columnShift;
      boolean newLeansRight = lineShift == 0 && columnShift != 0 ? columnShift < 0 : visualCaret.leansRight;

      if (desiredX >= 0) {
        newColumnNumber = myEditor.xyToVisualPosition(new Point(desiredX, Math.max(0, newLineNumber) * myEditor.getLineHeight())).column;
      }

      Document document = myEditor.getDocument();
      if (!editorSettings.isVirtualSpace() && lineShift == 0 && columnShift == 1) {
        int lastLine = document.getLineCount() - 1;
        if (lastLine < 0) lastLine = 0;
        if (newColumnNumber > EditorUtil.getLastVisualLineColumnNumber(myEditor, newLineNumber) &&
            newLineNumber < myEditor.logicalToVisualPosition(new LogicalPosition(lastLine, 0)).line) {
          newColumnNumber = 0;
          newLineNumber++;
        }
      }
      else if (!editorSettings.isVirtualSpace() && lineShift == 0 && columnShift == -1) {
        if (newColumnNumber < 0 && newLineNumber > 0) {
          newLineNumber--;
          newColumnNumber = EditorUtil.getLastVisualLineColumnNumber(myEditor, newLineNumber);
        }
      }

      if (newColumnNumber < 0) newColumnNumber = 0;

      // There is a possible case that caret is located at the first line and user presses 'Shift+Up'. We want to select all text
      // from the document start to the current caret position then. So, we have a dedicated flag for tracking that.
      boolean selectToDocumentStart = false;
      if (newLineNumber < 0) {
        selectToDocumentStart = true;
        newLineNumber = 0;

        // We want to move caret to the first column if it's already located at the first line and 'Up' is pressed.
        newColumnNumber = 0;
      }

      VisualPosition pos = new VisualPosition(newLineNumber, newColumnNumber);
      if (!myEditor.getSoftWrapModel().isInsideSoftWrap(pos)) {
        LogicalPosition log = myEditor.visualToLogicalPosition(new VisualPosition(newLineNumber, newColumnNumber, newLeansRight));
        int offset = myEditor.logicalPositionToOffset(log);
        if (offset >= document.getTextLength() && (!myEditor.myUseNewRendering || columnShift == 0)) {
          int lastOffsetColumn = myEditor.offsetToVisualPosition(document.getTextLength(), true, false).column;
          // We want to move caret to the last column if if it's located at the last line and 'Down' is pressed.
          if (lastOffsetColumn > newColumnNumber) {
            newColumnNumber = lastOffsetColumn;
            newLeansRight = true;
          }
        }
        if (!editorSettings.isCaretInsideTabs()) {
          CharSequence text = document.getCharsSequence();
          if (offset >= 0 && offset < document.getTextLength()) {
            if (text.charAt(offset) == '\t' && (columnShift <= 0 || offset == myOffset)) {
              if (columnShift <= 0) {
                newColumnNumber = myEditor.offsetToVisualPosition(offset, true, false).column;
              }
              else {
                SoftWrap softWrap = myEditor.getSoftWrapModel().getSoftWrap(offset + 1);
                // There is a possible case that tabulation symbol is the last document symbol represented on a visual line before
                // soft wrap. We can't just use column from 'offset + 1' because it would point on a next visual line.
                if (softWrap == null) {
                  newColumnNumber = myEditor.offsetToVisualPosition(offset + 1).column;
                }
                else {
                  newColumnNumber = EditorUtil.getLastVisualLineColumnNumber(myEditor, newLineNumber);
                }
              }
            }
          }
        }
      }

      pos = new VisualPosition(newLineNumber, newColumnNumber, newLeansRight);
      if (columnShift != 0 && lineShift == 0 && myEditor.getSoftWrapModel().isInsideSoftWrap(pos)) {
        LogicalPosition logical = myEditor.visualToLogicalPosition(pos);
        int softWrapOffset = myEditor.logicalPositionToOffset(logical);
        if (columnShift >= 0) {
          moveToOffset(softWrapOffset);
        }
        else {
          int line = myEditor.offsetToVisualLine(softWrapOffset - 1);
          moveToVisualPosition(new VisualPosition(line, EditorUtil.getLastVisualLineColumnNumber(myEditor, line)));
        }
      }
      else {
        moveToVisualPosition(pos);
        if (!editorSettings.isVirtualSpace() && columnShift == 0 && lastColumnNumber >=0) {
          setLastColumnNumber(lastColumnNumber);
        }
      }

      if (withSelection) {
        if (selectToDocumentStart) {
          setSelection(leadSelectionPosition, leadSelectionOffset, myEditor.offsetToVisualPosition(0), 0);
        }
        else if (pos.line >= myEditor.getVisibleLineCount()) {
          int endOffset = document.getTextLength();
          if (leadSelectionOffset < endOffset) {
            setSelection(leadSelectionPosition, leadSelectionOffset, myEditor.offsetToVisualPosition(endOffset), endOffset);
          }
        }
        else {
          int selectionStartToUse = leadSelectionOffset;
          VisualPosition selectionStartPositionToUse = leadSelectionPosition;
          if (isUnknownDirection() || oldOffset > getSelectionStart() && oldOffset < getSelectionEnd()) {
            if (getOffset() > leadSelectionOffset ^ getSelectionStart() < getSelectionEnd()) {
              selectionStartToUse = getSelectionEnd();
              selectionStartPositionToUse = getSelectionEndPosition();
            }
            else {
              selectionStartToUse = getSelectionStart();
              selectionStartPositionToUse = getSelectionStartPosition();
            }
          }
          setSelection(selectionStartPositionToUse, selectionStartToUse, getVisualPosition(), getOffset());
        }
      }
      else {
        removeSelection();
      }

      if (scrollToCaret) {
        myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }

      if (desiredX >= 0) {
        myDesiredX = desiredX;
      }

      EditorActionUtil.selectNonexpandableFold(myEditor);
    });
  }

  @Override
  public void moveToLogicalPosition(@NotNull final LogicalPosition pos) {
    myEditor.getCaretModel().doWithCaretMerging(() -> moveToLogicalPosition(pos, false, null, true));
  }


  private CaretEvent doMoveToLogicalPosition(@NotNull LogicalPosition pos,
                                             boolean locateBeforeSoftWrap,
                                             @NonNls @Nullable StringBuilder debugBuffer,
                                             boolean fireListeners) {
    assertIsDispatchThread();
    if (debugBuffer != null) {
      debugBuffer.append("Start moveToLogicalPosition(). Locate before soft wrap: ").append(locateBeforeSoftWrap).append(", position: ")
        .append(pos).append("\n");
    }
    myDesiredX = -1;
    validateCallContext();
    int column = pos.column;
    int line = pos.line;
    int softWrapLinesBefore = pos.softWrapLinesBeforeCurrentLogicalLine;
    int softWrapLinesCurrent = pos.softWrapLinesOnCurrentLogicalLine;
    int softWrapColumns = pos.softWrapColumnDiff;
    boolean leansForward = pos.leansForward;
    boolean leansRight = pos.visualPositionLeansRight;

    Document doc = myEditor.getDocument();

    int lineCount = doc.getLineCount();
    if (lineCount == 0) {
      if (debugBuffer != null) {
        debugBuffer.append("Resetting target logical line to zero as the document is empty\n");
      }
      line = 0;
    }
    else if (line > lineCount - 1) {
      if (debugBuffer != null) {
        debugBuffer.append("Resetting target logical line (").append(line).append(") to ").append(lineCount - 1)
          .append(" as it is greater than total document lines number\n");
      }
      line = lineCount - 1;
      softWrapLinesBefore = 0;
      softWrapLinesCurrent = 0;
    }

    EditorSettings editorSettings = myEditor.getSettings();

    if (!editorSettings.isVirtualSpace() && line < lineCount) {
      int lineEndOffset = doc.getLineEndOffset(line);
      final LogicalPosition endLinePosition = myEditor.offsetToLogicalPosition(lineEndOffset);
      int lineEndColumnNumber = endLinePosition.column;
      if (column > lineEndColumnNumber) {
        int oldColumn = column;
        column = lineEndColumnNumber;
        leansForward = true;
        leansRight = true;
        if (softWrapColumns != 0) {
          softWrapColumns -= column - lineEndColumnNumber;
        }
        if (debugBuffer != null) {
          debugBuffer.append("Resetting target logical column (").append(oldColumn).append(") to ").append(lineEndColumnNumber)
            .append(" because caret is not allowed to be located after line end (offset: ").append(lineEndOffset).append(", ")
            .append("logical position: ").append(endLinePosition).append("). Current soft wrap columns value: ").append(softWrapColumns)
            .append("\n");
        }
      }
    }

    myEditor.getFoldingModel().flushCaretPosition();

    VerticalInfo oldInfo = myCaretInfo;
    LogicalPosition oldCaretPosition = myLogicalCaret;
    VisualPosition oldVisualPosition = myVisibleCaret;

    LogicalPosition logicalPositionToUse;
    if (pos.visualPositionAware) {
      logicalPositionToUse = new LogicalPosition(
        line, column, softWrapLinesBefore, softWrapLinesCurrent, softWrapColumns, pos.foldedLines, pos.foldingColumnDiff, 
        leansForward, leansRight
      );
    }
    else {
      logicalPositionToUse = new LogicalPosition(line, column, leansForward);
    }
    final int offset = myEditor.logicalPositionToOffset(logicalPositionToUse);
    if (debugBuffer != null) {
      debugBuffer.append("Resulting logical position to use: ").append(logicalPositionToUse).append(". It's mapped to offset ").append(offset).append("\n");
    }

    FoldRegion collapsedAt = myEditor.getFoldingModel().getCollapsedRegionAtOffset(offset);

    if (collapsedAt != null && offset > collapsedAt.getStartOffset()) {
      if (debugBuffer != null) {
        debugBuffer.append("Scheduling expansion of fold region ").append(collapsedAt).append("\n");
      }
      Runnable runnable = () -> {
        FoldRegion[] allCollapsedAt = myEditor.getFoldingModel().fetchCollapsedAt(offset);
        for (FoldRegion foldRange : allCollapsedAt) {
          foldRange.setExpanded(true);
        }
      };

      mySkipChangeRequests = true;
      try {
        myEditor.getFoldingModel().runBatchFoldingOperation(runnable, false);
      }
      finally {
        mySkipChangeRequests = false;
      }
      logicalPositionToUse = logicalPositionToUse.visualPositionAware ? logicalPositionToUse.withoutVisualPositionInfo() : logicalPositionToUse;
    }

    setCurrentLogicalCaret(logicalPositionToUse);
    setLastColumnNumber(myLogicalCaret.column);
    myDesiredSelectionStartColumn = myDesiredSelectionEndColumn = -1;
    myVisibleCaret = myEditor.logicalToVisualPosition(myLogicalCaret);

    updateOffsetsFromLogicalPosition();
    if (debugBuffer != null) {
      debugBuffer.append("Storing offset ").append(myOffset).append(" (mapped from logical position ").append(myLogicalCaret).append(")\n");
    }
    LOG.assertTrue(myOffset >= 0 && myOffset <= myEditor.getDocument().getTextLength());

    updateVisualLineInfo();

    myEditor.updateCaretCursor();
    requestRepaint(oldInfo);

    if (locateBeforeSoftWrap && SoftWrapHelper.isCaretAfterSoftWrap(this)) {
      int lineToUse = myVisibleCaret.line - 1;
      if (lineToUse >= 0) {
        final VisualPosition visualPosition = new VisualPosition(lineToUse, EditorUtil.getLastVisualLineColumnNumber(myEditor, lineToUse));
        if (debugBuffer != null) {
          debugBuffer.append("Adjusting caret position by moving it before soft wrap. Moving to visual position ").append(visualPosition).append("\n");
        }
        final LogicalPosition logicalPosition = myEditor.visualToLogicalPosition(visualPosition);
        final int tmpOffset = myEditor.logicalPositionToOffset(logicalPosition);
        if (tmpOffset == myOffset) {
          boolean restore = myReportCaretMoves;
          myReportCaretMoves = false;
          try {
            moveToVisualPosition(visualPosition);
            return null;
          }
          finally {
            myReportCaretMoves = restore;
          }
        }
        else {
          LogMessageEx.error(LOG, "Invalid editor dimension mapping", "Expected to map visual position '" +
          visualPosition + "' to offset " + myOffset + " but got the following: -> logical position '" +
          logicalPosition + "'; -> offset " + tmpOffset + ". State: " + myEditor.dumpState());
        }
      }
    }

    if (myEditor.myUseNewRendering ? !oldVisualPosition.equals(myVisibleCaret) : 
        !oldCaretPosition.toVisualPosition().equals(myLogicalCaret.toVisualPosition())) {
      CaretEvent event = new CaretEvent(myEditor, this, oldCaretPosition, myLogicalCaret);
      if (fireListeners) {
        myEditor.getCaretModel().fireCaretPositionChanged(event);
      }
      else {
        return event;
      }
    }
    return null;
  }

  private void updateOffsetsFromLogicalPosition() {
    myOffset = myEditor.logicalPositionToOffset(myLogicalCaret);
    myVirtualSpaceOffset = myLogicalCaret.column - myEditor.offsetToLogicalPosition(myOffset).column;
  }

  private void setLastColumnNumber(int lastColumnNumber) {
    myLastColumnNumber = lastColumnNumber;
  }

  private void requestRepaint(VerticalInfo oldCaretInfo) {
    int lineHeight = myEditor.getLineHeight();
    Rectangle visibleArea = myEditor.getScrollingModel().getVisibleArea();
    final EditorGutterComponentEx gutter = myEditor.getGutterComponentEx();
    final EditorComponentImpl content = myEditor.getContentComponent();

    int updateWidth = myEditor.getScrollPane().getHorizontalScrollBar().getValue() + visibleArea.width;
    if (Math.abs(myCaretInfo.y - oldCaretInfo.y) <= 2 * lineHeight) {
      int minY = Math.min(oldCaretInfo.y, myCaretInfo.y);
      int maxY = Math.max(oldCaretInfo.y + oldCaretInfo.height, myCaretInfo.y + myCaretInfo.height);
      content.repaintEditorComponent(0, minY, updateWidth, maxY - minY);
      gutter.repaint(0, minY, gutter.getWidth(), maxY - minY);
    }
    else {
      content.repaintEditorComponent(0, oldCaretInfo.y, updateWidth, oldCaretInfo.height + lineHeight);
      gutter.repaint(0, oldCaretInfo.y, updateWidth, oldCaretInfo.height + lineHeight);
      content.repaintEditorComponent(0, myCaretInfo.y, updateWidth, myCaretInfo.height + lineHeight);
      gutter.repaint(0, myCaretInfo.y, updateWidth, myCaretInfo.height + lineHeight);
    }
  }

  @Override
  public void moveToVisualPosition(@NotNull final VisualPosition pos) {
    myEditor.getCaretModel().doWithCaretMerging(() -> moveToVisualPosition(pos, true));
  }

  void moveToVisualPosition(@NotNull VisualPosition pos, boolean fireListeners) {
    assertIsDispatchThread();
    validateCallContext();
    if (mySkipChangeRequests) {
      return;
    }
    if (myReportCaretMoves) {
      LogMessageEx.error(LOG, "Unexpected caret move request");
    }
    if (!myEditor.isStickySelection() && !myEditor.getCaretModel().isDocumentChanged && !pos.equals(myVisibleCaret)) {
      CopyPasteManager.getInstance().stopKillRings();
    }

    myDesiredX = -1;
    int column = pos.column;
    int line = pos.line;
    boolean leanRight = pos.leansRight;

    int lastLine = myEditor.getVisibleLineCount() - 1;
    if (lastLine <= 0) {
      lastLine = 0;
    }

    if (line > lastLine) {
      line = lastLine;
    }

    EditorSettings editorSettings = myEditor.getSettings();

    if (!editorSettings.isVirtualSpace()) {
      int lineEndColumn = EditorUtil.getLastVisualLineColumnNumber(myEditor, line);
      if (column > lineEndColumn && (!myEditor.myUseNewRendering || !myEditor.getSoftWrapModel().isInsideSoftWrap(pos))) {
        column = lineEndColumn;
        leanRight = true;
      }

      if (!myEditor.myUseNewRendering && column < 0 && line > 0) {
        line--;
        column = EditorUtil.getLastVisualLineColumnNumber(myEditor, line);
      }
    }

    myVisibleCaret = new VisualPosition(line, column, leanRight);

    VerticalInfo oldInfo = myCaretInfo;
    LogicalPosition oldPosition = myLogicalCaret;

    setCurrentLogicalCaret(myEditor.visualToLogicalPosition(myVisibleCaret));
    updateOffsetsFromLogicalPosition();
    LOG.assertTrue(myOffset >= 0 && myOffset <= myEditor.getDocument().getTextLength());

    updateVisualLineInfo();

    myEditor.getFoldingModel().flushCaretPosition();

    setLastColumnNumber(myLogicalCaret.column);
    myDesiredSelectionStartColumn = myDesiredSelectionEndColumn = -1;
    myEditor.updateCaretCursor();
    requestRepaint(oldInfo);

    if (fireListeners && !oldPosition.equals(myLogicalCaret)) {
      CaretEvent event = new CaretEvent(myEditor, this, oldPosition, myLogicalCaret);
      myEditor.getCaretModel().fireCaretPositionChanged(event);
    }
  }

  @Nullable
  CaretEvent moveToLogicalPosition(@NotNull LogicalPosition pos,
                                           boolean locateBeforeSoftWrap,
                                           @Nullable StringBuilder debugBuffer,
                                           boolean fireListeners) {
    if (mySkipChangeRequests) {
      return null;
    }
    if (myReportCaretMoves) {
      LogMessageEx.error(LOG, "Unexpected caret move request");
    }
    if (!myEditor.isStickySelection() && !myEditor.getCaretModel().isDocumentChanged && !pos.equals(myLogicalCaret)) {
      CopyPasteManager.getInstance().stopKillRings();
    }

    myReportCaretMoves = true;
    try {
      return doMoveToLogicalPosition(pos, locateBeforeSoftWrap, debugBuffer, fireListeners);
    }
    finally {
      myReportCaretMoves = false;
    }
  }

  private static void assertIsDispatchThread() {
    EditorImpl.assertIsDispatchThread();
  }

  private void validateCallContext() {
    LOG.assertTrue(!myEditor.getCaretModel().myIsInUpdate, "Caret model is in its update process. All requests are illegal at this point.");
  }

  private void releaseBulkCaretMarker() {
    if (savedBeforeBulkCaretMarker != null) {
      savedBeforeBulkCaretMarker.dispose();
      savedBeforeBulkCaretMarker = null;
    }
  }

  @Override
  public void dispose() {
    if (mySelectionMarker != null) {
      mySelectionMarker.dispose();
      mySelectionMarker = null;
    }
    releaseBulkCaretMarker();
    isValid = false;
  }

  @Override
  public boolean isUpToDate() {
    return !myEditor.getCaretModel().myIsInUpdate && !myReportCaretMoves;
  }

  @NotNull
  @Override
  public LogicalPosition getLogicalPosition() {
    validateCallContext();
    return myLogicalCaret;
  }

  @NotNull
  @Override
  public VisualPosition getVisualPosition() {
    validateCallContext();
    return myVisibleCaret;
  }

  @Override
  public int getOffset() {
    validateCallContext();
    return myOffset;
  }

  @Override
  public int getVisualLineStart() {
    return myVisualLineStart;
  }

  @Override
  public int getVisualLineEnd() {
    return myVisualLineEnd;
  }

  @NotNull
  private VerticalInfo createVerticalInfo(LogicalPosition position) {
    Document document = myEditor.getDocument();
    int logicalLine = position.line;
    if (logicalLine >= document.getLineCount()) {
      logicalLine = Math.max(0, document.getLineCount() - 1);
    }
    int startOffset = document.getLineStartOffset(logicalLine);
    int endOffset = document.getLineEndOffset(logicalLine);

    // There is a possible case that active logical line is represented on multiple lines due to soft wraps processing.
    // We want to highlight those visual lines as 'active' then, so, we calculate 'y' position for the logical line start
    // and height in accordance with the number of occupied visual lines.
    VisualPosition visualPosition = myEditor.offsetToVisualPosition(document.getLineStartOffset(logicalLine));
    int y = myEditor.myUseNewRendering ? myEditor.visibleLineToY(visualPosition.line) :  myEditor.visualPositionToXY(visualPosition).y;
    int lineHeight = myEditor.getLineHeight();
    int height = lineHeight;
    List<? extends SoftWrap> softWraps = myEditor.getSoftWrapModel().getSoftWrapsForRange(startOffset, endOffset);
    for (SoftWrap softWrap : softWraps) {
      height += StringUtil.countNewLines(softWrap.getText()) * lineHeight;
    }

    return new VerticalInfo(y, height);
  }

  /**
   * Recalculates caret visual position without changing its logical position (called when soft wraps are changing)
   */
  void updateVisualPosition() {
    VerticalInfo oldInfo = myCaretInfo;
    LogicalPosition visUnawarePos = new LogicalPosition(myLogicalCaret.line, myLogicalCaret.column);
    setCurrentLogicalCaret(visUnawarePos);
    myVisibleCaret = myEditor.logicalToVisualPosition(myLogicalCaret);
    updateVisualLineInfo();

    myEditor.updateCaretCursor();
    requestRepaint(oldInfo);
  }

  private void updateVisualLineInfo() {
    myVisualLineStart = myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(myVisibleCaret.line, 0)));
    myVisualLineEnd = myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(myVisibleCaret.line + 1, 0)));
  }

  void updateCaretPosition(@NotNull final DocumentEventImpl event) {
    final DocumentEx document = myEditor.getDocument();
    if (document.isInBulkUpdate()) return;
    boolean performSoftWrapAdjustment = event.getNewLength() > 0 // We want to put caret just after the last added symbol
                                        // There is a possible case that the user removes text just before the soft wrap. We want to keep caret
                                        // on a visual line with soft wrap start then.
                                        || myEditor.getSoftWrapModel().getSoftWrap(event.getOffset()) != null;

    if (event.isWholeTextReplaced()) {
      int newLength = document.getTextLength();
      if (myOffset == newLength - event.getNewLength() + event.getOldLength() || newLength == 0) {
        moveToOffset(newLength, performSoftWrapAdjustment);
      }
      else {
        try {
          final int line = event.translateLineViaDiff(myLogicalCaret.line);
          moveToLogicalPosition(new LogicalPosition(line, myLogicalCaret.column), performSoftWrapAdjustment, null, true);
        }
        catch (FilesTooBigForDiffException e1) {
          LOG.info(e1);
          moveToOffset(0);
        }
      }
    }
    else {
      int startOffset = event.getOffset();
      int oldEndOffset = startOffset + event.getOldLength();

      int newOffset = myOffset;

      if (myOffset > oldEndOffset || myOffset == oldEndOffset && needToShiftWhiteSpaces(event)) {
        newOffset += event.getNewLength() - event.getOldLength();
      }
      else if (myOffset >= startOffset && myOffset <= oldEndOffset) {
        newOffset = Math.min(newOffset, startOffset + event.getNewLength());
      }

      newOffset = Math.min(newOffset, document.getTextLength());

      if (myOffset != startOffset) {
        LogicalPosition pos = myEditor.offsetToLogicalPosition(newOffset);
        moveToLogicalPosition(new LogicalPosition(pos.line, pos.column + myVirtualSpaceOffset), // retain caret in the virtual space
                            performSoftWrapAdjustment, null, true);
      }
      else {
        moveToOffset(newOffset, performSoftWrapAdjustment);
      }
    }

    updateVisualLineInfo();
  }

  private boolean needToShiftWhiteSpaces(final DocumentEvent e) {
    if (!CharArrayUtil.containsOnlyWhiteSpaces(e.getNewFragment()) || CharArrayUtil.containLineBreaks(e.getNewFragment()))
      return e.getOldLength() > 0;
    if (e.getOffset() == 0) return false;
    final char charBefore = myEditor.getDocument().getCharsSequence().charAt(e.getOffset() - 1);
    //final char charAfter = myEditor.getDocument().getCharsSequence().charAt(e.getOffset() + e.getNewLength());
    return Character.isWhitespace(charBefore)/* || !Character.isWhitespace(charAfter)*/;
  }

  private void setCurrentLogicalCaret(@NotNull LogicalPosition position) {
    myLogicalCaret = position;
    myCaretInfo = createVerticalInfo(position);
  }

  int getWordAtCaretStart() {
    Document document = myEditor.getDocument();
    int offset = getOffset();
    if (offset == 0) return 0;
    int lineNumber = getLogicalPosition().line;
    int newOffset = offset - 1;
    int minOffset = lineNumber > 0 ? document.getLineEndOffset(lineNumber - 1) : 0;
    boolean camel = myEditor.getSettings().isCamelWords();
    for (; newOffset > minOffset; newOffset--) {
      if (EditorActionUtil.isWordOrLexemeStart(myEditor, newOffset, camel)) break;
    }

    return newOffset;
  }

  int getWordAtCaretEnd() {
    Document document = myEditor.getDocument();
    int offset = getOffset();

    if (offset >= document.getTextLength() - 1 || document.getLineCount() == 0) return offset;

    int newOffset = offset + 1;

    int lineNumber = getLogicalPosition().line;
    int maxOffset = document.getLineEndOffset(lineNumber);
    if (newOffset > maxOffset) {
      if (lineNumber + 1 >= document.getLineCount()) return offset;
      maxOffset = document.getLineEndOffset(lineNumber + 1);
    }
    boolean camel = myEditor.getSettings().isCamelWords();
    for (; newOffset < maxOffset; newOffset++) {
      if (EditorActionUtil.isWordOrLexemeEnd(myEditor, newOffset, camel)) break;
    }

    return newOffset;
  }

  private CaretImpl cloneWithoutSelection() {
    CaretImpl clone = new CaretImpl(myEditor);
    clone.myLogicalCaret = myLogicalCaret;
    clone.myCaretInfo = myCaretInfo;
    clone.myVisibleCaret = myVisibleCaret;
    clone.myOffset = myOffset;
    clone.myVirtualSpaceOffset = myVirtualSpaceOffset;
    clone.myVisualLineStart = myVisualLineStart;
    clone.myVisualLineEnd = myVisualLineEnd;
    clone.savedBeforeBulkCaretMarker = savedBeforeBulkCaretMarker;
    clone.mySkipChangeRequests = mySkipChangeRequests;
    clone.myLastColumnNumber = myLastColumnNumber;
    clone.myReportCaretMoves = myReportCaretMoves;
    clone.myDesiredX = myDesiredX;
    clone.myDesiredSelectionStartColumn = -1;
    clone.myDesiredSelectionEndColumn = -1;
    return clone;
  }

  @Nullable
  @Override
  public Caret clone(boolean above) {
    assertIsDispatchThread();
    int lineShift = above ? -1 : 1;
    final CaretImpl clone = cloneWithoutSelection();
    final int newSelectionStartOffset;
    final int newSelectionEndOffset;
    final int newSelectionStartColumn;
    final int newSelectionEndColumn;
    final VisualPosition newSelectionStartPosition;
    final VisualPosition newSelectionEndPosition;
    final boolean hasNewSelection;
    if (hasSelection() || myDesiredSelectionStartColumn >=0 || myDesiredSelectionEndColumn >= 0) {
      VisualPosition startPosition = getSelectionStartPosition();
      VisualPosition endPosition = getSelectionEndPosition();
      VisualPosition leadPosition = getLeadSelectionPosition();
      boolean leadIsStart = leadPosition.equals(startPosition);
      boolean leadIsEnd = leadPosition.equals(endPosition);
      LogicalPosition selectionStart = myEditor.visualToLogicalPosition(leadIsStart || leadIsEnd ? leadPosition : startPosition);
      LogicalPosition selectionEnd = myEditor.visualToLogicalPosition(leadIsEnd ? startPosition : endPosition);
      newSelectionStartColumn = myDesiredSelectionStartColumn < 0 ? selectionStart.column : myDesiredSelectionStartColumn;
      newSelectionEndColumn = myDesiredSelectionEndColumn < 0 ? selectionEnd.column : myDesiredSelectionEndColumn;
      LogicalPosition newSelectionStart = truncate(selectionStart.line + lineShift, newSelectionStartColumn);
      LogicalPosition newSelectionEnd = truncate(selectionEnd.line + lineShift, newSelectionEndColumn);
      newSelectionStartOffset = myEditor.logicalPositionToOffset(newSelectionStart);
      newSelectionEndOffset = myEditor.logicalPositionToOffset(newSelectionEnd);
      newSelectionStartPosition = myEditor.logicalToVisualPosition(newSelectionStart);
      newSelectionEndPosition = myEditor.logicalToVisualPosition(newSelectionEnd);
      hasNewSelection = !newSelectionStart.equals(newSelectionEnd);
    }
    else {
      newSelectionStartOffset = 0;
      newSelectionEndOffset = 0;
      newSelectionStartPosition = null;
      newSelectionEndPosition = null;
      hasNewSelection = false;
      newSelectionStartColumn = -1;
      newSelectionEndColumn = -1;
    }
    LogicalPosition oldPosition = getLogicalPosition();
    int newLine = oldPosition.line + lineShift;
    if (newLine < 0 || newLine >= myEditor.getDocument().getLineCount()) {
      Disposer.dispose(clone);
      return null;
    }
    clone.moveToLogicalPosition(new LogicalPosition(newLine, myLastColumnNumber), false, null, false);
    clone.myLastColumnNumber = myLastColumnNumber;
    clone.myDesiredX = myDesiredX >= 0 ? myDesiredX : getCurrentX();
    clone.myDesiredSelectionStartColumn = newSelectionStartColumn;
    clone.myDesiredSelectionEndColumn = newSelectionEndColumn;

    if (myEditor.getCaretModel().addCaret(clone, true)) {
      if (hasNewSelection) {
        myEditor.getCaretModel().doWithCaretMerging(
          () -> clone.setSelection(newSelectionStartPosition, newSelectionStartOffset, newSelectionEndPosition, newSelectionEndOffset));
        if (!clone.isValid()) {
          return null;
        }
      }
      myEditor.getScrollingModel().scrollTo(clone.getLogicalPosition(), ScrollType.RELATIVE);
      return clone;
    }
    else {
      Disposer.dispose(clone);
      return null;
    }
  }

  private LogicalPosition truncate(int line, int column) {
    if (line < 0) {
      return new LogicalPosition(0, 0);
    }
    else if (line >= myEditor.getDocument().getLineCount()) {
      return myEditor.offsetToLogicalPosition(myEditor.getDocument().getTextLength());
    }
    else {
      return new LogicalPosition(line, column);
    }
  }

  /**
   * @return  information on whether current selection's direction in known
   * @see #setUnknownDirection(boolean)
   */
  boolean isUnknownDirection() {
    return myUnknownDirection;
  }

  /**
   * There is a possible case that we don't know selection's direction. For example, a user might triple-click editor (select the
   * whole line). We can't say what selection end is a {@link #getLeadSelectionOffset() leading end} then. However, that matters
   * in a situation when a user clicks before or after that line holding Shift key. It's expected that the selection is expanded
   * up to that point than.
   * <p/>
   * That's why we allow to specify that the direction is unknown and {@link #isUnknownDirection() expose this information}
   * later.
   * <p/>
   * <b>Note:</b> when this method is called with <code>'true'</code>, subsequent calls are guaranteed to return <code>'true'</code>
   * until selection is changed. 'Unknown direction' flag is automatically reset then.
   *
   */
  void setUnknownDirection(boolean unknownDirection) {
    myUnknownDirection = unknownDirection;
  }

  @Override
  public int getSelectionStart() {
    validateContext(false);
    if (hasSelection()) {
      RangeMarker marker = mySelectionMarker;
      if (marker != null) {
        return marker.getStartOffset();
      }
    }
    return getOffset();
  }

  @NotNull
  @Override
  public VisualPosition getSelectionStartPosition() {
    validateContext(false);
    VisualPosition position;
    if (hasSelection() && mySelectionMarker != null) {
      position = getRangeMarkerStartPosition();
      if (position == null) {
        VisualPosition startPosition = myEditor.offsetToVisualPosition(mySelectionMarker.getStartOffset(), true, false);
        VisualPosition endPosition = myEditor.offsetToVisualPosition(mySelectionMarker.getEndOffset(), false, true);
        position = startPosition.after(endPosition) ? endPosition : startPosition;
      }
    }
    else {
      position = isVirtualSelectionEnabled() ? getVisualPosition() : 
                 myEditor.offsetToVisualPosition(getOffset(), myLogicalCaret.leansForward, false);
    }
    if (hasVirtualSelection()) {
      position = new VisualPosition(position.line, position.column + myStartVirtualOffset);
    }
    return position;
  }

  @Override
  public int getSelectionEnd() {
    validateContext(false);
    if (hasSelection()) {
      RangeMarker marker = mySelectionMarker;
      if (marker != null) {
        return marker.getEndOffset();
      }
    }
    return getOffset();
  }

  @NotNull
  @Override
  public VisualPosition getSelectionEndPosition() {
    validateContext(false);
    VisualPosition position;
    if (hasSelection() && mySelectionMarker != null) {
      position = getRangeMarkerEndPosition();
      if (position == null) {
        VisualPosition startPosition = myEditor.offsetToVisualPosition(mySelectionMarker.getStartOffset(), true, false);
        VisualPosition endPosition = myEditor.offsetToVisualPosition(mySelectionMarker.getEndOffset(), false, true);
        position = startPosition.after(endPosition) ? startPosition : endPosition;
      }
    }
    else {
      position = isVirtualSelectionEnabled() ? getVisualPosition() : 
                 myEditor.offsetToVisualPosition(getOffset(), myLogicalCaret.leansForward, false);
    }
    if (hasVirtualSelection()) {
      position = new VisualPosition(position.line, position.column + myEndVirtualOffset);
    }
    return position;
  }

  @Override
  public boolean hasSelection() {
    validateContext(false);
    RangeMarker marker = mySelectionMarker;
    return marker != null && marker.isValid() && (marker.getEndOffset() > marker.getStartOffset()
                                                  || isVirtualSelectionEnabled() && myEndVirtualOffset > myStartVirtualOffset);
  }

  @Override
  public void setSelection(int startOffset, int endOffset) {
    setSelection(startOffset, endOffset, true);
  }

  @Override
  public void setSelection(int startOffset, int endOffset, boolean updateSystemSelection) {
    doSetSelection(myEditor.offsetToVisualPosition(startOffset, true, false), startOffset, 
                   myEditor.offsetToVisualPosition(endOffset, false, true), endOffset, 
                   false, updateSystemSelection);
  }

  @Override
  public void setSelection(int startOffset, @Nullable VisualPosition endPosition, int endOffset) {
    VisualPosition startPosition;
    if (hasSelection()) {
      startPosition = getLeadSelectionPosition();
    }
    else {
      startPosition = myEditor.offsetToVisualPosition(startOffset, true, false);
    }
    setSelection(startPosition, startOffset, endPosition, endOffset);
  }

  @Override
  public void setSelection(@Nullable VisualPosition startPosition, int startOffset, @Nullable VisualPosition endPosition, int endOffset) {
    setSelection(startPosition, startOffset, endPosition, endOffset, true);
  }

  @Override
  public void setSelection(@Nullable VisualPosition startPosition, int startOffset, @Nullable VisualPosition endPosition, int endOffset, 
                           boolean updateSystemSelection) {
    VisualPosition startPositionToUse = startPosition == null ? myEditor.offsetToVisualPosition(startOffset, true, false) : startPosition;
    VisualPosition endPositionToUse = endPosition == null ? myEditor.offsetToVisualPosition(endOffset, false, true) : endPosition;
    doSetSelection(startPositionToUse, startOffset, endPositionToUse, endOffset, true, updateSystemSelection);
  }

  private void doSetSelection(@NotNull final VisualPosition startPosition,
                              final int _startOffset,
                              @NotNull final VisualPosition endPosition,
                              final int _endOffset,
                              final boolean visualPositionAware,
                              final boolean updateSystemSelection)
  {
    myEditor.getCaretModel().doWithCaretMerging(() -> {
      int startOffset = _startOffset;
      int endOffset = _endOffset;
      myUnknownDirection = false;
      final Document doc = myEditor.getDocument();

      validateContext(true);

      int textLength = doc.getTextLength();
      if (startOffset < 0 || startOffset > textLength) {
        LOG.error("Wrong startOffset: " + startOffset + ", textLength=" + textLength);
      }
      if (endOffset < 0 || endOffset > textLength) {
        LOG.error("Wrong endOffset: " + endOffset + ", textLength=" + textLength);
      }

      if (!visualPositionAware && startOffset == endOffset) {
        removeSelection();
        return;
      }

  /* Normalize selection */
      boolean switchedOffsets = false;
      if (startOffset > endOffset) {
        int tmp = startOffset;
        startOffset = endOffset;
        endOffset = tmp;
        switchedOffsets = true;
      }

      FoldingModelEx foldingModel = myEditor.getFoldingModel();
      FoldRegion startFold = foldingModel.getCollapsedRegionAtOffset(startOffset);
      if (startFold != null && startFold.getStartOffset() < startOffset) {
        startOffset = startFold.getStartOffset();
      }

      FoldRegion endFold = foldingModel.getCollapsedRegionAtOffset(endOffset);
      if (endFold != null && endFold.getStartOffset() < endOffset) {
        // All visual positions that lay at collapsed fold region placeholder are mapped to the same offset. Hence, there are
        // at least two distinct situations - selection end is located inside collapsed fold region placeholder and just before it.
        // We want to expand selection to the fold region end at the former case and keep selection as-is at the latest one.
        endOffset = endFold.getEndOffset();
      }

      int oldSelectionStart;
      int oldSelectionEnd;

      if (hasSelection()) {
        oldSelectionStart = getSelectionStart();
        oldSelectionEnd = getSelectionEnd();
        if (oldSelectionStart == startOffset && oldSelectionEnd == endOffset && !visualPositionAware) return;
      }
      else {
        oldSelectionStart = oldSelectionEnd = getOffset();
      }

      RangeMarker marker = mySelectionMarker;
      if (marker != null) {
        marker.dispose();
      }

      marker = doc.createRangeMarker(startOffset, endOffset);
      myStartVirtualOffset = 0;
      myEndVirtualOffset = 0;
      if (visualPositionAware) {
        if (endPosition.after(startPosition)) {
          setRangeMarkerStartPosition(startPosition);
          setRangeMarkerEndPosition(endPosition);
          setRangeMarkerEndPositionIsLead(false);
        }
        else {
          setRangeMarkerStartPosition(endPosition);
          setRangeMarkerEndPosition(startPosition);
          setRangeMarkerEndPositionIsLead(true);
        }

        if (isVirtualSelectionEnabled() &&
            myEditor.getDocument().getLineNumber(startOffset) == myEditor.getDocument().getLineNumber(endOffset)) {
          int endLineColumn = myEditor.offsetToVisualPosition(endOffset).column;
          int startDiff =
            EditorUtil.isAtLineEnd(myEditor, switchedOffsets ? endOffset : startOffset) ? startPosition.column - endLineColumn : 0;
          int endDiff =
            EditorUtil.isAtLineEnd(myEditor, switchedOffsets ? startOffset : endOffset) ? endPosition.column - endLineColumn : 0;
          myStartVirtualOffset = Math.max(0, Math.min(startDiff, endDiff));
          myEndVirtualOffset = Math.max(0, Math.max(startDiff, endDiff));
        }
      }
      mySelectionMarker = marker;

      myEditor.getSelectionModel().fireSelectionChanged(oldSelectionStart, oldSelectionEnd, startOffset, endOffset);

      if (updateSystemSelection) {
        updateSystemSelection();
      }
    });
  }

  private void updateSystemSelection() {
    if (GraphicsEnvironment.isHeadless()) return;

    final Clipboard clip = myEditor.getComponent().getToolkit().getSystemSelection();
    if (clip != null) {
      clip.setContents(new StringSelection(myEditor.getSelectionModel().getSelectedText(true)), EmptyClipboardOwner.INSTANCE);
    }
  }

  @Override
  public void removeSelection() {
    if (myEditor.isStickySelection()) {
      // Most of our 'change caret position' actions (like move caret to word start/end etc) remove active selection.
      // However, we don't want to do that for 'sticky selection'.
      return;
    }
    myEditor.getCaretModel().doWithCaretMerging(() -> {
      validateContext(true);
      int caretOffset = getOffset();
      RangeMarker marker = mySelectionMarker;
      if (marker != null) {
        int startOffset = marker.getStartOffset();
        int endOffset = marker.getEndOffset();
        marker.dispose();
        mySelectionMarker = null;
        myStartVirtualOffset = 0;
        myEndVirtualOffset = 0;
        myEditor.getSelectionModel().fireSelectionChanged(startOffset, endOffset, caretOffset, caretOffset);
      }
    });
  }

  @Override
  public int getLeadSelectionOffset() {
    validateContext(false);
    int caretOffset = getOffset();
    if (hasSelection()) {
      RangeMarker marker = mySelectionMarker;
      if (marker != null) {
        int startOffset = marker.getStartOffset();
        int endOffset = marker.getEndOffset();
        if (caretOffset != startOffset && caretOffset != endOffset) {
          // Try to check if current selection is tweaked by fold region.
          FoldingModelEx foldingModel = myEditor.getFoldingModel();
          FoldRegion foldRegion = foldingModel.getCollapsedRegionAtOffset(caretOffset);
          if (foldRegion != null) {
            if (foldRegion.getStartOffset() == startOffset) {
              return endOffset;
            }
            else if (foldRegion.getEndOffset() == endOffset) {
              return startOffset;
            }
          }
        }

        if (caretOffset == endOffset) {
          return startOffset;
        }
        else {
          return endOffset;
        }
      }
    }
    return caretOffset;
  }

  @NotNull
  @Override
  public VisualPosition getLeadSelectionPosition() {
    RangeMarker marker = mySelectionMarker;
    VisualPosition caretPosition = getVisualPosition();
    if (isVirtualSelectionEnabled() && !hasSelection()) {
      return caretPosition;
    }
    if (marker == null) {
      return caretPosition;
    }

    if (isRangeMarkerEndPositionIsLead()) {
      VisualPosition result = getRangeMarkerEndPosition();
      if (result == null) {
        return getSelectionEndPosition();
      }
      else {
        if (hasVirtualSelection()) {
          result = new VisualPosition(result.line, result.column + myEndVirtualOffset);
        }
        return result;
      }
    }
    else {
      VisualPosition result = getRangeMarkerStartPosition();
      if (result == null) {
        return getSelectionStartPosition();
      }
      else {
        if (hasVirtualSelection()) {
          result = new VisualPosition(result.line, result.column + myStartVirtualOffset);
        }
        return result;
      }
    }
  }

  @Override
  public void selectLineAtCaret() {
    validateContext(true);
    myEditor.getCaretModel().doWithCaretMerging(() -> SelectionModelImpl.doSelectLineAtCaret(myEditor));
  }

  @Override
  public void selectWordAtCaret(final boolean honorCamelWordsSettings) {
    validateContext(true);
    myEditor.getCaretModel().doWithCaretMerging(() -> {
      removeSelection();
      final EditorSettings settings = myEditor.getSettings();
      boolean camelTemp = settings.isCamelWords();

      final boolean needOverrideSetting = camelTemp && !honorCamelWordsSettings;
      if (needOverrideSetting) {
        settings.setCamelWords(false);
      }

      try {
        EditorActionHandler handler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
        handler.execute(myEditor, this, myEditor.getDataContext());
      }
      finally {
        if (needOverrideSetting) {
          settings.resetCamelWords();
        }
      }
    });
  }

  @Nullable
  @Override
  public String getSelectedText() {
    if (!hasSelection()) {
      return null;
    }
    CharSequence text = myEditor.getDocument().getCharsSequence();
    int selectionStart = getSelectionStart();
    int selectionEnd = getSelectionEnd();
    String selectedText = text.subSequence(selectionStart, selectionEnd).toString();
    if (isVirtualSelectionEnabled() && myEndVirtualOffset > myStartVirtualOffset) {
      int padding = myEndVirtualOffset - myStartVirtualOffset;
      StringBuilder builder = new StringBuilder(selectedText.length() + padding);
      builder.append(selectedText);
      for (int i = 0; i < padding; i++) {
        builder.append(' ');
      }
      return builder.toString();
    }
    else {
      return selectedText;
    }
  }

  private void validateContext(boolean isWrite) {
    if (!myEditor.getComponent().isShowing()) return;
    if (isWrite) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }
    else {
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
  }

  private boolean isVirtualSelectionEnabled() {
    return myEditor.isColumnMode();
  }

  boolean hasVirtualSelection() {
    validateContext(false);
    RangeMarker marker = mySelectionMarker;
    return marker != null && marker.isValid() && isVirtualSelectionEnabled() && myEndVirtualOffset > myStartVirtualOffset;
  }

  private int getCurrentX() {
    return myEditor.visualPositionToXY(myVisibleCaret).x;
  }

  @Override
  @NotNull
  public EditorImpl getEditor() {
    return myEditor;
  }

  @Override
  public String toString() {
    return "Caret at " + myVisibleCaret + (mySelectionMarker == null ? "" : (", selection marker: " + mySelectionMarker.toString()));
  }

  @Override
  public boolean isAtRtlLocation() {
    return myEditor.myUseNewRendering && myEditor.myView.isRtlLocation(myVisibleCaret);
  }

  @Override
  public boolean isAtBidiRunBoundary() {
    return myEditor.myUseNewRendering && myEditor.myView.isAtBidiRunBoundary(myVisibleCaret);
  }

  @NotNull
  @Override
  public String dumpState() {
    return "{valid: " + isValid +
           ", offset: " + myOffset +
           ", logical pos: " + myLogicalCaret +
           ", visual pos: " + myVisibleCaret +
           ", visual line start: " + myVisualLineStart +
           ", visual line end: " + myVisualLineEnd +
           ", savedBeforeBulkCaretMarker: " + savedBeforeBulkCaretMarker +
           ", skip change requests: " + mySkipChangeRequests +
           ", desired selection start column: " + myDesiredSelectionStartColumn +
           ", desired selection end column: " + myDesiredSelectionEndColumn +
           ", report caret moves: " + myReportCaretMoves +
           ", desired x: " + myDesiredX +
           ", selection marker: " + mySelectionMarker +
           ", rangeMarker start position: " + myRangeMarkerStartPosition +
           ", rangeMarker end position: " + myRangeMarkerEndPosition +
           ", rangeMarker end position is lead: " + myRangeMarkerEndPositionIsLead +
           ", selection start before change: " + startBefore +
           ", selection end after change: " + endBefore +
           ", unknown direction: " + myUnknownDirection +
           ", virtual space offset: " + myVirtualSpaceOffset +
           ", virtual selection start offset: " + myStartVirtualOffset +
           ", virtual selection end offset: " + myEndVirtualOffset + '}';
  }

  /**
   * Encapsulates information about target vertical range info - its <code>'y'</code> coordinate and height in pixels.
   */
  private static class VerticalInfo {
    public final int y;
    public final int height;

    private VerticalInfo(int y, int height) {
      this.y = y;
      this.height = height;
    }
  }

  @Nullable
  private VisualPosition getRangeMarkerStartPosition() {
    invalidateRangeMarkerVisualPositions(mySelectionMarker);
    return myRangeMarkerStartPosition;
  }

  private void setRangeMarkerStartPosition(@NotNull VisualPosition startPosition) {
    myRangeMarkerStartPosition = startPosition;
  }

  @Nullable
  private VisualPosition getRangeMarkerEndPosition() {
    invalidateRangeMarkerVisualPositions(mySelectionMarker);
    return myRangeMarkerEndPosition;
  }

  void setRangeMarkerEndPosition(@NotNull VisualPosition endPosition) {
    myRangeMarkerEndPosition = endPosition;
  }

  private boolean isRangeMarkerEndPositionIsLead() {
    return myRangeMarkerEndPositionIsLead;
  }

  void setRangeMarkerEndPositionIsLead(boolean endPositionIsLead) {
    myRangeMarkerEndPositionIsLead = endPositionIsLead;
  }

  private void invalidateRangeMarkerVisualPositions(RangeMarker marker) {
    SoftWrapModelImpl model = myEditor.getSoftWrapModel();
    if (!myEditor.offsetToVisualPosition(marker.getStartOffset(), true, false).equals(myRangeMarkerStartPosition) &&
        model.getSoftWrap(marker.getStartOffset()) == null ||
        !myEditor.offsetToVisualPosition(marker.getEndOffset(), false, true).equals(myRangeMarkerEndPosition)
        && model.getSoftWrap(marker.getEndOffset()) == null) {
      myRangeMarkerStartPosition = null;
      myRangeMarkerEndPosition = null;
    }
  }
}
