/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jun 18, 2002
 * Time: 9:12:05 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.diagnostic.LogMessageEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentBulkUpdateListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapHelper;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.diff.FilesTooBigForDiffException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

public class CaretModelImpl implements CaretModel, PrioritizedDocumentListener, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.CaretModelImpl");

  private final EditorImpl myEditor;
  private final EventDispatcher<CaretListener> myCaretListeners = EventDispatcher.create(CaretListener.class);
  private LogicalPosition myLogicalCaret;
  private VerticalInfo myCaretInfo;
  private VisualPosition myVisibleCaret;
  private int myOffset;
  private int myVisualLineStart;
  private int myVisualLineEnd;
  private TextAttributes myTextAttributes;
  private boolean myIsInUpdate;
  private RangeMarker savedBeforeBulkCaretMarker;
  private boolean myIgnoreWrongMoves = false;
  private boolean mySkipChangeRequests;

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
   * There is a possible case that user defined non-monospaced font for editor. That means that various symbols have different
   * visual widths. That means that if we move caret vertically it may deviate to the left/right. However, we can try to preserve
   * its initial visual position when possible.
   * <p/>
   * This field holds desired value for visual <code>'x'</code> caret coordinate (negative value if no coordinate should be preserved).
   */
  private int myDesiredX = -1;

  public CaretModelImpl(EditorImpl editor) {
    myEditor = editor;
    myLogicalCaret = new LogicalPosition(0, 0);
    myVisibleCaret = new VisualPosition(0, 0);
    myCaretInfo = new VerticalInfo(0, 0);
    myOffset = 0;
    myVisualLineStart = 0;
    Document doc = editor.getDocument();
    myVisualLineEnd = doc.getLineCount() > 1 ? doc.getLineStartOffset(1) : doc.getLineCount() == 0 ? 0 : doc.getLineEndOffset(0);
    DocumentBulkUpdateListener bulkUpdateListener = new DocumentBulkUpdateListener() {
      @Override
      public void updateStarted(@NotNull Document doc) {
        if (doc != myEditor.getDocument() || myOffset > doc.getTextLength() || savedBeforeBulkCaretMarker != null) return;
        savedBeforeBulkCaretMarker = doc.createRangeMarker(myOffset, myOffset);
      }

      @Override
      public void updateFinished(@NotNull Document doc) {
        if (doc != myEditor.getDocument() || myIsInUpdate) return;
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
      }
    };
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(DocumentBulkUpdateListener.TOPIC, bulkUpdateListener);
  }

  private void releaseBulkCaretMarker() {
    if (savedBeforeBulkCaretMarker != null) {
      savedBeforeBulkCaretMarker.dispose();
      savedBeforeBulkCaretMarker = null;
    }
  }

  @Override
  public void moveToVisualPosition(@NotNull VisualPosition pos) {
    assertIsDispatchThread();
    validateCallContext();
    if (mySkipChangeRequests) {
      return;
    }
    if (myReportCaretMoves) {
      LogMessageEx.error(LOG, "Unexpected caret move request");
    }
    if (!myEditor.isStickySelection()) {
      CopyPasteManager.getInstance().stopKillRings();
    }

    myDesiredX = -1;
    int column = pos.column;
    int line = pos.line;

    if (column < 0) column = 0;

    if (line < 0) line = 0;

    int lastLine = myEditor.getVisibleLineCount() - 1;
    if (lastLine <= 0) {
      lastLine = 0;
    }

    if (line > lastLine) {
      line = lastLine;
    }

    EditorSettings editorSettings = myEditor.getSettings();

    if (!editorSettings.isVirtualSpace() && line <= lastLine) {
      int lineEndColumn = EditorUtil.getLastVisualLineColumnNumber(myEditor, line);
      if (column > lineEndColumn) {
        column = lineEndColumn;
      }

      if (column < 0 && line > 0) {
        line--;
        column = EditorUtil.getLastVisualLineColumnNumber(myEditor, line);
      }
    }

    myVisibleCaret = new VisualPosition(line, column);

    VerticalInfo oldInfo = myCaretInfo;
    LogicalPosition oldPosition = myLogicalCaret;

    setCurrentLogicalCaret(myEditor.visualToLogicalPosition(myVisibleCaret));
    myOffset = myEditor.logicalPositionToOffset(myLogicalCaret);
    LOG.assertTrue(myOffset >= 0 && myOffset <= myEditor.getDocument().getTextLength());

    myVisualLineStart = myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(myVisibleCaret.line, 0)));
    myVisualLineEnd = myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(myVisibleCaret.line + 1, 0)));

    myEditor.getFoldingModel().flushCaretPosition();

    myEditor.setLastColumnNumber(myVisibleCaret.column);
    myEditor.updateCaretCursor();
    requestRepaint(oldInfo);

    if (!oldPosition.equals(myLogicalCaret)) {
      CaretEvent event = new CaretEvent(myEditor, oldPosition, myLogicalCaret);
      myCaretListeners.getMulticaster().caretPositionChanged(event);
    }
  }

  private void assertIsDispatchThread() {
    myEditor.assertIsDispatchThread();
  }

  @Override
  public void moveToOffset(int offset) {
    moveToOffset(offset, false);
  }

  @Override
  public void moveToOffset(int offset, boolean locateBeforeSoftWrap) {
    assertIsDispatchThread();
    validateCallContext();
    if (mySkipChangeRequests) {
      return;
    }
    final LogicalPosition logicalPosition = myEditor.offsetToLogicalPosition(offset);
    CaretEvent event = moveToLogicalPosition(logicalPosition, locateBeforeSoftWrap, null, false);
    final LogicalPosition positionByOffsetAfterMove = myEditor.offsetToLogicalPosition(myOffset);
    if (!myIgnoreWrongMoves && !positionByOffsetAfterMove.equals(logicalPosition)) {
      StringBuilder debugBuffer = new StringBuilder();
      moveToLogicalPosition(logicalPosition, locateBeforeSoftWrap, debugBuffer, true);
      int textStart = Math.max(0, Math.min(offset, myOffset) - 1);
      final DocumentEx document = myEditor.getDocument();
      int textEnd = Math.min(document.getTextLength() - 1, Math.max(offset, myOffset) + 1);
      CharSequence text = document.getCharsSequence().subSequence(textStart, textEnd);
      StringBuilder positionToOffsetTrace = new StringBuilder();
      int inverseOffset = myEditor.logicalPositionToOffset(logicalPosition, positionToOffsetTrace);
      LogMessageEx.error(
        LOG, "caret moved to wrong offset. Please submit a dedicated ticket and attach current editor's text to it.",
        String.format(
          "Requested: offset=%d, logical position='%s' but actual: offset=%d, logical position='%s' (%s). %s%n"
          + "interested text [%d;%d): '%s'%n debug trace: %s%nLogical position -> offset ('%s'->'%d') trace: %s",
          offset, logicalPosition, myOffset, myLogicalCaret, positionByOffsetAfterMove, myEditor.dumpState(),
          textStart, textEnd, text, debugBuffer, logicalPosition, inverseOffset, positionToOffsetTrace
        ));
    }
    if (event != null) {
      myCaretListeners.getMulticaster().caretPositionChanged(event);
      EditorActionUtil.selectNonexpandableFold(myEditor);
    }
  }

  public void setIgnoreWrongMoves(boolean ignoreWrongMoves) {
    myIgnoreWrongMoves = ignoreWrongMoves;
  }

  @Override
  public void moveCaretRelatively(int columnShift,
                                  int lineShift,
                                  boolean withSelection,
                                  boolean blockSelection,
                                  boolean scrollToCaret) {
    assertIsDispatchThread();
    if (mySkipChangeRequests) {
      return;
    }
    if (myReportCaretMoves) {
      LogMessageEx.error(LOG, "Unexpected caret move request");
    }
    if (!myEditor.isStickySelection()) {
      CopyPasteManager.getInstance().stopKillRings();
    }
    SelectionModelImpl selectionModel = myEditor.getSelectionModel();
    final int leadSelectionOffset = selectionModel.getLeadSelectionOffset();
    LogicalPosition blockSelectionStart = selectionModel.hasBlockSelection()
                                          ? selectionModel.getBlockStart()
                                          : getLogicalPosition();
    EditorSettings editorSettings = myEditor.getSettings();
    VisualPosition visualCaret = getVisualPosition();

    int desiredX = myDesiredX;
    if (columnShift == 0) {
      if (myDesiredX < 0) {
        desiredX = myEditor.visualPositionToXY(visualCaret).x;
      }
    }
    else {
      myDesiredX = desiredX = -1;
    }

    int newLineNumber = visualCaret.line + lineShift;
    int newColumnNumber = visualCaret.column + columnShift;
    if (desiredX >= 0 && !ApplicationManager.getApplication().isUnitTestMode()) {
      newColumnNumber = myEditor.xyToVisualPosition(new Point(desiredX, Math.max(0, newLineNumber) * myEditor.getLineHeight())).column;
    }

    Document document = myEditor.getDocument();
    if (!editorSettings.isVirtualSpace() && columnShift == 0 && getLogicalPosition().softWrapLinesOnCurrentLogicalLine <= 0) {
      newColumnNumber = myEditor.getLastColumnNumber();
    }
    else if (!editorSettings.isVirtualSpace() && lineShift == 0 && columnShift == 1) {
      int lastLine = document.getLineCount() - 1;
      if (lastLine < 0) lastLine = 0;
      if (EditorModificationUtil.calcAfterLineEnd(myEditor) >= 0 &&
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
      desiredX = -1;
    }

    VisualPosition pos = new VisualPosition(newLineNumber, newColumnNumber);
    int lastColumnNumber = newColumnNumber;
    if (!editorSettings.isCaretInsideTabs() && !myEditor.getSoftWrapModel().isInsideSoftWrap(pos)) {
      LogicalPosition log = myEditor.visualToLogicalPosition(new VisualPosition(newLineNumber, newColumnNumber));
      int offset = myEditor.logicalPositionToOffset(log);
      if (offset >= document.getTextLength()) {
        int lastOffsetColumn = myEditor.offsetToVisualPosition(document.getTextLength()).column;
        // We want to move caret to the last column if if it's located at the last line and 'Down' is pressed.
        newColumnNumber = lastColumnNumber = Math.max(lastOffsetColumn, newColumnNumber);
        desiredX = -1;
      }
      CharSequence text = document.getCharsSequence();
      if (offset >= 0 && offset < document.getTextLength()) {
        if (text.charAt(offset) == '\t' && (columnShift <= 0 || offset == myOffset)) {
          if (columnShift <= 0) {
            newColumnNumber = myEditor.offsetToVisualPosition(offset).column;
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

    pos = new VisualPosition(newLineNumber, newColumnNumber);
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
      if (!editorSettings.isVirtualSpace() && columnShift == 0) {
        myEditor.setLastColumnNumber(lastColumnNumber);
      }
    }

    if (withSelection) {
      if (blockSelection) {
        selectionModel.setBlockSelection(blockSelectionStart, getLogicalPosition());
      }
      else {
        if (selectToDocumentStart) {
          selectionModel.setSelection(leadSelectionOffset, 0);
        }
        else if (pos.line >= myEditor.getVisibleLineCount()) {
          if (leadSelectionOffset < document.getTextLength()) {
            selectionModel.setSelection(leadSelectionOffset, document.getTextLength());
          }
        }
        else {
          int selectionStartToUse = leadSelectionOffset;
          if (selectionModel.isUnknownDirection()) {
            if (getOffset() > leadSelectionOffset) {
              selectionStartToUse = Math.min(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
            }
            else {
              selectionStartToUse = Math.max(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
            }
          }
          selectionModel.setSelection(selectionStartToUse, getVisualPosition(), getOffset());
        }
      }
    }
    else {
      selectionModel.removeSelection();
    }

    if (scrollToCaret) {
      myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }

    if (desiredX >= 0) {
      myDesiredX = desiredX;
    }

    EditorActionUtil.selectNonexpandableFold(myEditor);
  }

  @Override
  public void moveToLogicalPosition(@NotNull LogicalPosition pos) {
    moveToLogicalPosition(pos, false, null, true);
  }

  @Nullable
  private CaretEvent moveToLogicalPosition(@NotNull LogicalPosition pos,
                                           boolean locateBeforeSoftWrap,
                                           @Nullable StringBuilder debugBuffer,
                                           boolean fireListeners) {
    if (mySkipChangeRequests) {
      return null;
    }
    if (myReportCaretMoves) {
      LogMessageEx.error(LOG, "Unexpected caret move request");
    }
    if (!myEditor.isStickySelection()) {
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

  private CaretEvent doMoveToLogicalPosition(@NotNull LogicalPosition pos,
                                             boolean locateBeforeSoftWrap,
                                             @NonNls @Nullable StringBuilder debugBuffer,
                                             boolean fireListeners) {
    assertIsDispatchThread();
    if (debugBuffer != null) {
      debugBuffer.append("Start moveToLogicalPosition(). Locate before soft wrap: " + locateBeforeSoftWrap + ", position: " + pos + "\n");
    }
    myDesiredX = -1;
    validateCallContext();
    int column = pos.column;
    int line = pos.line;
    int softWrapLinesBefore = pos.softWrapLinesBeforeCurrentLogicalLine;
    int softWrapLinesCurrent = pos.softWrapLinesOnCurrentLogicalLine;
    int softWrapColumns = pos.softWrapColumnDiff;

    Document doc = myEditor.getDocument();

    if (column < 0) {
      if (debugBuffer != null) {
        debugBuffer.append("Resetting target logical column to zero as it is negative (").append(column).append(")\n");
      }
      column = 0;
      softWrapColumns = 0;
    }
    if (line < 0) {
      if (debugBuffer != null) {
        debugBuffer.append("Resetting target logical line to zero as it is negative (").append(line).append(")\n");
      }
      line = 0;
      softWrapLinesBefore = 0;
      softWrapLinesCurrent = 0;
    }

    int lineCount = doc.getLineCount();
    if (lineCount == 0) {
      if (debugBuffer != null) {
        debugBuffer.append("Resetting target logical line to zero as the document is empty\n");
      }
      line = 0;
    }
    else if (line > lineCount - 1) {
      if (debugBuffer != null) {
        debugBuffer.append("Resetting target logical line (" + line + ") to " + (lineCount - 1) + " as it is greater than total document lines number\n");
      }
      line = lineCount - 1;
      softWrapLinesBefore = 0;
      softWrapLinesCurrent = 0;
    }

    EditorSettings editorSettings = myEditor.getSettings();

    if (!editorSettings.isVirtualSpace() && line < lineCount && !myEditor.getSelectionModel().hasBlockSelection()) {
      int lineEndOffset = doc.getLineEndOffset(line);
      final LogicalPosition endLinePosition = myEditor.offsetToLogicalPosition(lineEndOffset);
      int lineEndColumnNumber = endLinePosition.column;
      if (column > lineEndColumnNumber) {
        int oldColumn = column;
        column = lineEndColumnNumber;
        if (softWrapColumns != 0) {
          softWrapColumns -= column - lineEndColumnNumber;
        }
        if (debugBuffer != null) {
          debugBuffer.append(
            "Resetting target logical column (" + oldColumn + ") to " + lineEndColumnNumber +
            " because caret is not allowed to be located after line end (offset: " +lineEndOffset + ", "
            + "logical position: " + endLinePosition+ "). Current soft wrap columns value: " + softWrapColumns+ "\n");
        }
      }
    }

    myEditor.getFoldingModel().flushCaretPosition();

    VerticalInfo oldInfo = myCaretInfo;
    LogicalPosition oldCaretPosition = myLogicalCaret;

    LogicalPosition logicalPositionToUse;
    if (pos.visualPositionAware) {
      logicalPositionToUse = new LogicalPosition(
        line, column, softWrapLinesBefore, softWrapLinesCurrent, softWrapColumns, pos.foldedLines, pos.foldingColumnDiff
      );
    }
    else {
      logicalPositionToUse = new LogicalPosition(line, column);
    }
    setCurrentLogicalCaret(logicalPositionToUse);
    final int offset = myEditor.logicalPositionToOffset(myLogicalCaret);
    if (debugBuffer != null) {
      debugBuffer.append("Resulting logical position to use: " + myLogicalCaret+
                         ". It's mapped to offset " + offset+ "\n");
    }

    FoldRegion collapsedAt = myEditor.getFoldingModel().getCollapsedRegionAtOffset(offset);

    if (collapsedAt != null && offset > collapsedAt.getStartOffset()) {
      if (debugBuffer != null) {
        debugBuffer.append("Scheduling expansion of fold region ").append(collapsedAt).append("\n");
      }
      Runnable runnable = new Runnable() {
        @Override
        public void run() {
          FoldRegion[] allCollapsedAt = myEditor.getFoldingModel().fetchCollapsedAt(offset);
          for (FoldRegion foldRange : allCollapsedAt) {
            foldRange.setExpanded(true);
          }
        }
      };

      mySkipChangeRequests = true;
      try {
        myEditor.getFoldingModel().runBatchFoldingOperation(runnable, false);
      }
      finally {
        mySkipChangeRequests = false;
      }
    }

    myEditor.setLastColumnNumber(myLogicalCaret.column);
    myVisibleCaret = myEditor.logicalToVisualPosition(myLogicalCaret);

    myOffset = myEditor.logicalPositionToOffset(myLogicalCaret);
    if (debugBuffer != null) {
      debugBuffer.append("Storing offset " + myOffset + " (mapped from logical position " + myLogicalCaret + ")\n");
    }
    LOG.assertTrue(myOffset >= 0 && myOffset <= myEditor.getDocument().getTextLength());

    myVisualLineStart = myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(myVisibleCaret.line, 0)));
    myVisualLineEnd = myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(myVisibleCaret.line + 1, 0)));

    myEditor.updateCaretCursor();
    requestRepaint(oldInfo);

    if (locateBeforeSoftWrap && SoftWrapHelper.isCaretAfterSoftWrap(myEditor)) {
      int lineToUse = myVisibleCaret.line - 1;
      if (lineToUse >= 0) {
        final VisualPosition visualPosition = new VisualPosition(lineToUse, EditorUtil.getLastVisualLineColumnNumber(myEditor, lineToUse));
        if (debugBuffer != null) {
          debugBuffer.append("Adjusting caret position by moving it before soft wrap. Moving to visual position "+ visualPosition+"\n");
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
          LogMessageEx.error(LOG, "Invalid editor dimension mapping", String.format(
            "Expected to map visual position '%s' to offset %d but got the following: -> logical position '%s'; -> offset %d. "
            + "State: %s", visualPosition, myOffset, logicalPosition, tmpOffset, myEditor.dumpState()
          ));
        }
      }
    }

    if (!oldCaretPosition.toVisualPosition().equals(myLogicalCaret.toVisualPosition())) {
      CaretEvent event = new CaretEvent(myEditor, oldCaretPosition, myLogicalCaret);
      if (fireListeners) {
        myCaretListeners.getMulticaster().caretPositionChanged(event);
      }
      else {
        return event;
      }
    }
    return null;
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
  public boolean isUpToDate() {
    return !myIsInUpdate && !myReportCaretMoves;
  }

  @Override
  @NotNull
  public LogicalPosition getLogicalPosition() {
    validateCallContext();
    return myLogicalCaret;
  }

  private void validateCallContext() {
    LOG.assertTrue(!myIsInUpdate, "Caret model is in its update process. All requests are illegal at this point.");
  }

  @Override
  @NotNull
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

  @Override
  public void addCaretListener(@NotNull CaretListener listener) {
    myCaretListeners.addListener(listener);
  }

  @Override
  public void removeCaretListener(@NotNull CaretListener listener) {
    myCaretListeners.removeListener(listener);
  }

  @Override
  public TextAttributes getTextAttributes() {
    if (myTextAttributes == null) {
      myTextAttributes = new TextAttributes();
      myTextAttributes.setBackgroundColor(myEditor.getColorsScheme().getColor(EditorColors.CARET_ROW_COLOR));
    }

    return myTextAttributes;
  }

  public void reinitSettings() {
    myTextAttributes = null;
  }

  @Override
  public void documentChanged(DocumentEvent e) {
    finishUpdate();

    DocumentEventImpl event = (DocumentEventImpl)e;
    final DocumentEx document = myEditor.getDocument();
    boolean performSoftWrapAdjustment = e.getNewLength() > 0 // We want to put caret just after the last added symbol
                                        // There is a possible case that the user removes text just before the soft wrap. We want to keep caret
                                        // on a visual line with soft wrap start then.
                                        || myEditor.getSoftWrapModel().getSoftWrap(e.getOffset()) != null;

    if (event.isWholeTextReplaced()) {
      int newLength = document.getTextLength();
      if (myOffset == newLength - e.getNewLength() + e.getOldLength() || newLength == 0) {
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
      if (document.isInBulkUpdate()) return;
      int startOffset = e.getOffset();
      int oldEndOffset = startOffset + e.getOldLength();

      int newOffset = myOffset;

      if (myOffset > oldEndOffset || myOffset == oldEndOffset && needToShiftWhiteSpaces(e)) {
        newOffset += e.getNewLength() - e.getOldLength();
      }
      else if (myOffset >= startOffset && myOffset <= oldEndOffset) {
        newOffset = Math.min(newOffset, startOffset + e.getNewLength());
      }

      newOffset = Math.min(newOffset, document.getTextLength());

      // if (newOffset != myOffset) {
      moveToOffset(newOffset, performSoftWrapAdjustment);
      //}
      //else {
      //  moveToVisualPosition(oldPosition);
      //}
    }

    myVisualLineStart = myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(myVisibleCaret.line, 0)));
    myVisualLineEnd = myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(myVisibleCaret.line + 1, 0)));
  }

  private void finishUpdate() {
    myIsInUpdate = false;
  }

  private boolean needToShiftWhiteSpaces(final DocumentEvent e) {
    if (!CharArrayUtil.containsOnlyWhiteSpaces(e.getNewFragment()) || CharArrayUtil.containLineBreaks(e.getNewFragment()))
      return e.getOldLength() > 0;
    if (e.getOffset() == 0) return false;
    final char charBefore = myEditor.getDocument().getCharsSequence().charAt(e.getOffset() - 1);
    //final char charAfter = myEditor.getDocument().getCharsSequence().charAt(e.getOffset() + e.getNewLength());
    return Character.isWhitespace(charBefore)/* || !Character.isWhitespace(charAfter)*/;
  }

  @Override
  public void beforeDocumentChange(DocumentEvent e) {
    myIsInUpdate = true;
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.CARET_MODEL;
  }

  private void setCurrentLogicalCaret(@NotNull LogicalPosition position) {
    myLogicalCaret = position;
    myCaretInfo = createVerticalInfo(position);
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
    int y = myEditor.visualPositionToXY(visualPosition).y;
    int lineHeight = myEditor.getLineHeight();
    int height = lineHeight;
    List<? extends SoftWrap> softWraps = myEditor.getSoftWrapModel().getSoftWrapsForRange(startOffset, endOffset);
    for (SoftWrap softWrap : softWraps) {
      height += StringUtil.countNewLines(softWrap.getText()) * lineHeight;
    }

    return new VerticalInfo(y, height);
  }

  @Override
  public void dispose() {
    releaseBulkCaretMarker();
  }

  /**
   * Encapsulates information about target vertical range info - its <code>'y'</code> coordinate and height in pixels.
   */
  public static class VerticalInfo {
    public final int y;
    public final int height;

    private VerticalInfo(int y, int height) {
      this.y = y;
      this.height = height;
    }
  }
}
