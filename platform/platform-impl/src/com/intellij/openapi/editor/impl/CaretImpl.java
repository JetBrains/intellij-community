// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.diagnostic.Dumpable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.CustomizedDataContext;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.AttachmentFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.CaretState;
import com.intellij.openapi.editor.CaretVisualAttributes;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.EditorThreading;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.InlayModel;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.SoftWrap;
import com.intellij.openapi.editor.SoftWrapModel;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapHelper;
import com.intellij.openapi.editor.impl.view.EditorPainter;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.TextRangeScalarUtil;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.diff.FilesTooBigForDiffException;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.Point;
import java.awt.Rectangle;

@ApiStatus.Internal
public final class CaretImpl extends UserDataHolderBase implements Caret, Dumpable {
  private static final Logger LOG = Logger.getInstance(CaretImpl.class);

  private final EditorImpl myEditor;
  private final DocumentEx myDocument;
  private final CaretModelImpl myCaretModel;

  private boolean isValid = true;
  private Throwable myDisposalTrace;

  private LogicalPosition myLogicalCaret;
  private VerticalInfo myVerticalInfo;
  private VisualPosition myVisibleCaret;
  private volatile PositionMarker myPositionMarker;
  private boolean myLeansTowardsLargerOffsets;
  private int myLogicalColumnAdjustment;
  private int myVisualColumnAdjustment;
  private int myVisualLineStart;
  private int myVisualLineEnd;
  private CaretVisualAttributes myAttributes;

  private boolean mySkipChangeRequests;
  private int myDocumentUpdateCounter;

  /**
   * This field holds initial horizontal caret position during vertical navigation. It's used to determine target position when
   * moving to the new line. It is stored in pixels, not in columns, to account for non-monospaced fonts as well.
   * <p/>
   * Negative value means no coordinate should be preserved.
   */
  private int myDesiredX = -1;
  private int myDesiredSelectionStartColumn = -1;
  private int myDesiredSelectionEndColumn = -1;
  private int myColumnNumberForCloning = -1;

  private volatile SelectionMarker mySelectionMarker;
  private volatile VisualPosition mySelectionStartPosition;
  private volatile VisualPosition mySelectionEndPosition;
  private volatile boolean mySelectionEndPositionIsLead;
  private boolean mySelectionUnknownDirection;

  CaretImpl(@NotNull EditorImpl editor, @NotNull CaretModelImpl caretModel) {
    myEditor = editor;
    myDocument = editor.getElfDocument();
    myCaretModel = caretModel;
    myLogicalCaret = new LogicalPosition(0, 0);
    myVisibleCaret = new VisualPosition(0, 0);
    myPositionMarker = new PositionMarker(0);
    myVisualLineStart = 0;
    myVisualLineEnd = getInitialVisualLineEnd(myDocument);
    myDocumentUpdateCounter = myDocument.getModificationSequence();
  }

  @Override
  public void moveToOffset(int offset) {
    moveToOffset(offset, false);
  }

  @Override
  public void moveToOffset(final int offset, final boolean locateBeforeSoftWrap) {
    ThreadingAssertions.assertEventDispatchThread();
    assertNotUpdating();
    if (mySkipChangeRequests) {
      return;
    }
    myCaretModel.doWithCaretMerging(() -> {
      LogicalPosition logicalPosition = myEditor.offsetToLogicalPosition(offset);
      CaretEvent event = moveToLogicalPosition(logicalPosition, locateBeforeSoftWrap, null, true, false);
      final LogicalPosition positionByOffsetAfterMove = myEditor.offsetToLogicalPosition(getOffset());
      if (!positionByOffsetAfterMove.equals(logicalPosition)) {
        StringBuilder debugBuffer = new StringBuilder();
        moveToLogicalPosition(logicalPosition, locateBeforeSoftWrap, debugBuffer, true, true);
        int actualOffset = getOffset();
        int textStart = Math.max(0, Math.min(offset, actualOffset) - 1);
        int textEnd = Math.min(myDocument.getTextLength() - 1, Math.max(offset, actualOffset) + 1);
        CharSequence text = myDocument.getCharsSequence().subSequence(textStart, textEnd);
        int inverseOffset = myEditor.logicalPositionToOffset(logicalPosition);
        LOG.error(
          "caret moved to wrong offset. Please submit a dedicated ticket and attach current editor's text to it.",
          new Throwable(),
          AttachmentFactory.createContext(
            "Requested: offset=" + offset + ", logical position='" + logicalPosition + "' but actual: offset=" +
            actualOffset + ", logical position='" + myLogicalCaret + "' (" + positionByOffsetAfterMove + "). " + myEditor.dumpState() +
            "\ninterested text [" + textStart + ";" + textEnd + "): '" + text + "'\n debug trace: " + debugBuffer +
            "\nLogical position -> offset ('" + logicalPosition + "'->'" + inverseOffset + "')"));
      }
      if (event != null) {
        myCaretModel.fireCaretPositionChanged(event);
        EditorActionUtil.selectNonexpandableFold(myEditor);
      }
    });
  }

  @Override
  public @NotNull CaretModel getCaretModel() {
    return myCaretModel;
  }

  @Override
  public boolean isValid() {
    return isValid;
  }

  @Override
  public void moveCaretRelatively(final int _columnShift, final int lineShift, final boolean withSelection, final boolean scrollToCaret) {
    ThreadingAssertions.assertEventDispatchThread();
    if (mySkipChangeRequests) {
      return;
    }
    stopKillRings();
    myCaretModel.doWithCaretMerging(() -> {
      updateCachedStateIfNeeded();

      int oldOffset = getOffset();
      int columnShift = _columnShift;
      if (withSelection && lineShift == 0) {
        if (columnShift == -1) {
          int column;
          while ((column = myVisibleCaret.column + columnShift - (hasSelection() && oldOffset == getSelectionEnd() ? 1 : 0)) >= 0 &&
                 myEditor.getInlayModel().hasInlineElementAt(new VisualPosition(myVisibleCaret.line, column))) {
            columnShift--;
          }
        }
        else if (columnShift == 1) {
          while (myEditor.getInlayModel().hasInlineElementAt(
            new VisualPosition(myVisibleCaret.line,
                               myVisibleCaret.column + columnShift - (hasSelection() && oldOffset == getSelectionStart() ? 0 : 1)))) {
            columnShift++;
          }
        }
      }
      final int leadSelectionOffset = getLeadSelectionOffset();
      final VisualPosition leadSelectionPosition = getLeadSelectionPosition();
      EditorSettings editorSettings = myEditor.getSettings();
      VisualPosition visualCaret = getVisualPosition();

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
        newColumnNumber = myEditor.xyToVisualPosition(new Point(desiredX, myEditor.visualLineToY(newLineNumber))).column;
      }

      if (!editorSettings.isVirtualSpace() && lineShift == 0 && columnShift == 1) {
        int lastLine = myDocument.getLineCount() - 1;
        if (lastLine < 0) lastLine = 0;
        if (newColumnNumber > EditorUtil.getLastVisualLineColumnNumber(myEditor, newLineNumber) &&
            newLineNumber < myEditor.logicalToVisualPosition(new LogicalPosition(lastLine, 0)).line) {
          newColumnNumber = 0;
          newLineNumber++;
        }
      }
      else if (lineShift == 0 && columnShift == -1) {
        if (newColumnNumber < 0 && newLineNumber > 0) {
          newLineNumber--;
          if (editorSettings.isVirtualSpace()) {
            newColumnNumber = myEditor.offsetToVisualPosition(Math.max(0, oldOffset - 1)).column;
          } else {
            newColumnNumber = EditorUtil.getLastVisualLineColumnNumber(myEditor, newLineNumber);
          }
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
        int offset = myEditor.visualPositionToOffset(new VisualPosition(newLineNumber, newColumnNumber, newLeansRight));
        if (offset >= myDocument.getTextLength() && columnShift == 0) {
          int lastOffsetColumn = myEditor.offsetToVisualPosition(myDocument.getTextLength(), true, false).column;
          // We want to move caret to the last column if it's located at the last line and 'Down' is pressed.
          if (lastOffsetColumn > newColumnNumber) {
            newColumnNumber = lastOffsetColumn;
            newLeansRight = true;
          }
        }
        if (!editorSettings.isCaretInsideTabs()) {
          CharSequence text = myDocument.getCharsSequence();
          if (offset >= 0 && offset < myDocument.getTextLength()) {
            if (text.charAt(offset) == '\t' && (columnShift <= 0 || offset == oldOffset) && !isAtRtlLocation()) {
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
        int softWrapOffset = myEditor.visualPositionToOffset(pos);
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
      }

      if (withSelection) {
        if (selectToDocumentStart) {
          setSelection(leadSelectionPosition, leadSelectionOffset, myEditor.offsetToVisualPosition(0), 0);
        }
        else if (pos.line >= myEditor.getVisibleLineCount()) {
          int endOffset = myDocument.getTextLength();
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
  public void moveToLogicalPosition(final @NotNull LogicalPosition pos) {
    myCaretModel.doWithCaretMerging(() -> moveToLogicalPosition(pos, false, null, false, true));
  }


  private CaretEvent doMoveToLogicalPosition(@NotNull LogicalPosition pos,
                                             boolean locateBeforeSoftWrap,
                                             @NonNls @Nullable StringBuilder debugBuffer,
                                             boolean adjustForInlays,
                                             boolean fireListeners) {
    ThreadingAssertions.assertEventDispatchThread();
    checkDisposal();
    updateCachedStateIfNeeded();
    if (debugBuffer != null) {
      debugBuffer.append("Start moveToLogicalPosition(). Locate before soft wrap: ").append(locateBeforeSoftWrap).append(", position: ")
        .append(pos).append("\n");
    }
    myDesiredX = -1;
    assertNotUpdating();
    int column = pos.column;
    int line = pos.line;
    boolean leansForward = pos.leansForward;
    int lineCount = myDocument.getLineCount();
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
    }

    EditorSettings editorSettings = myEditor.getSettings();

    if (!editorSettings.isVirtualSpace() && line < lineCount) {
      int lineEndOffset = myDocument.getLineEndOffset(line);
      final LogicalPosition endLinePosition = myEditor.offsetToLogicalPosition(lineEndOffset);
      int lineEndColumnNumber = endLinePosition.column;
      if (column > lineEndColumnNumber) {
        int oldColumn = column;
        column = lineEndColumnNumber;
        leansForward = true;
        if (debugBuffer != null) {
          debugBuffer.append("Resetting target logical column (").append(oldColumn).append(") to ").append(lineEndColumnNumber)
            .append(" because caret is not allowed to be located after line end (offset: ").append(lineEndOffset).append(", ")
            .append("logical position: ").append(endLinePosition).append(").\n");
        }
      }
    }

    VerticalInfo oldVerticalInfo = myVerticalInfo;
    LogicalPosition oldCaretPosition = myLogicalCaret;
    VisualPosition oldVisualPosition = myVisibleCaret;
    boolean oldInVirtualSpace = isInVirtualSpace();

    LogicalPosition logicalPositionToUse = new LogicalPosition(line, column, leansForward);
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
        myEditor.getFoldingModel().runBatchFoldingOperation(runnable, false, false, true);
      }
      finally {
        mySkipChangeRequests = false;
      }
    }

    myEditor.getFoldingModel().flushCaretPosition(this);

    myLogicalCaret = logicalPositionToUse;
    myColumnNumberForCloning = -1;
    myDesiredSelectionStartColumn = myDesiredSelectionEndColumn = -1;
    myVisibleCaret = myEditor.logicalToVisualPosition(myLogicalCaret);
    myVisualColumnAdjustment = 0;

    updateOffsetsFromLogicalPosition();
    int newOffset = getOffset();
    if (debugBuffer != null) {
      debugBuffer.append("Storing offset ").append(newOffset).append(" (mapped from logical position ").append(myLogicalCaret).append(")\n");
    }

    if (adjustForInlays) {
      VisualPosition correctPosition = EditorUtil.inlayAwareOffsetToVisualPosition(myEditor, newOffset);
      assert correctPosition.line == myVisibleCaret.line;
      myVisualColumnAdjustment = correctPosition.column - myVisibleCaret.column;
      myVisibleCaret = correctPosition;
    }

    updateVisualLineInfo();

    myEditor.updateCaretCursor();
    requestRepaint(oldVerticalInfo);

    if (locateBeforeSoftWrap && SoftWrapHelper.isCaretAfterSoftWrap(this)) {
      int lineToUse = myVisibleCaret.line - 1;
      if (lineToUse >= 0) {
        final VisualPosition visualPosition = new VisualPosition(lineToUse, EditorUtil.getLastVisualLineColumnNumber(myEditor, lineToUse));
        if (debugBuffer != null) {
          debugBuffer.append("Adjusting caret position by moving it before soft wrap. Moving to visual position ").append(visualPosition).append("\n");
        }
        final int tmpOffset = myEditor.visualPositionToOffset(visualPosition);
        if (tmpOffset == newOffset) {
          moveToVisualPosition(visualPosition);
          return null;
        }
        else {
          LOG.error("Invalid editor dimension mapping", new Throwable(), AttachmentFactory.createContext(
            "Expected to map visual position '" +
            visualPosition + "' to offset " + newOffset + " but got the following: -> offset " + tmpOffset +
            ". State: " + myEditor.dumpState()));
        }
      }
    }

    if (!oldVisualPosition.equals(myVisibleCaret) || !oldCaretPosition.equals(myLogicalCaret)) {
      if (oldInVirtualSpace || isInVirtualSpace()) {
        myCaretModel.validateEditorSize();
      }
      CaretEvent event = new CaretEvent(this, oldCaretPosition, myLogicalCaret);
      if (fireListeners) {
        myCaretModel.fireCaretPositionChanged(event);
      }
      else {
        return event;
      }
    }
    return null;
  }

  private void updateOffsetsFromLogicalPosition() {
    int offset = myEditor.logicalPositionToOffset(myLogicalCaret);
    PositionMarker oldMarker = myPositionMarker;
    if (!oldMarker.isValid() || oldMarker.getStartOffset() != offset || oldMarker.getEndOffset() != offset) {
      myPositionMarker = new PositionMarker(offset);
      oldMarker.dispose();
    }
    myLeansTowardsLargerOffsets = myLogicalCaret.leansForward;
    myLogicalColumnAdjustment = myLogicalCaret.column - myEditor.offsetToLogicalPosition(offset).column;
  }

  private void requestRepaint(VerticalInfo oldVerticalInfo) {
    if (oldVerticalInfo == null) oldVerticalInfo = new VerticalInfo(0, 0, myEditor.getLineHeight());
    if (myVerticalInfo == null) myVerticalInfo = new VerticalInfo(0, 0, myEditor.getLineHeight());

    int oldY, oldHeight, newY, newHeight;
    if (oldVerticalInfo.logicalLineY == myVerticalInfo.logicalLineY &&
        oldVerticalInfo.logicalLineHeight == myVerticalInfo.logicalLineHeight) {
      // caret moved within the same soft-wrapped line, repaint only original and target visual lines
      oldY = oldVerticalInfo.y;
      newY = myVerticalInfo.y;
      oldHeight = newHeight = myEditor.getLineHeight();
    }
    else {
      // caret moved between different (possible soft-wrapped) lines, repaint whole lines
      // (to repaint soft-wrap markers and line numbers in gutter)
      oldY = oldVerticalInfo.logicalLineY;
      oldHeight = oldVerticalInfo.logicalLineHeight;
      newY = myVerticalInfo.logicalLineY;
      newHeight = myVerticalInfo.logicalLineHeight;
    }

    Rectangle visibleArea = myEditor.getScrollingModel().getVisibleArea();
    final EditorGutterComponentEx gutter = myEditor.getGutterComponentEx();
    final EditorComponentImpl content = myEditor.getContentComponent();
    int editorUpdateWidth = myEditor.getScrollPane().getHorizontalScrollBar().getValue() + visibleArea.width;
    int gutterUpdateWidth = gutter.getWidth();
    int additionalRepaintHeight = this == myCaretModel.getPrimaryCaret() && Registry.is("editor.adjust.right.margin")
                                  && EditorPainter.isMarginShown(myEditor) ? 1 : 0;
    if ((oldY <= newY + newHeight) && (oldY + oldHeight >= newY)) { // repaint regions overlap
      int y = Math.min(oldY, newY);
      int height = Math.max(oldY + oldHeight, newY + newHeight) - y;
      content.repaintEditorComponent(0, y - additionalRepaintHeight, editorUpdateWidth, height + additionalRepaintHeight);
      gutter.repaint(0, y, gutterUpdateWidth, height);
    }
    else {
      content.repaintEditorComponent(0, oldY - additionalRepaintHeight, editorUpdateWidth, oldHeight + additionalRepaintHeight);
      gutter.repaint(0, oldY, gutterUpdateWidth, oldHeight);
      content.repaintEditorComponent(0, newY - additionalRepaintHeight, editorUpdateWidth, newHeight + additionalRepaintHeight);
      gutter.repaint(0, newY, gutterUpdateWidth, newHeight);
    }
  }

  @Override
  public void moveToVisualPosition(final @NotNull VisualPosition pos) {
    moveToVisualPosition(pos, true);
  }

  private void moveToVisualPosition(final @NotNull VisualPosition pos, boolean fireListeners) {
    myCaretModel.doWithCaretMerging(() -> doMoveToVisualPosition(pos, fireListeners));
  }

  void doMoveToVisualPosition(@NotNull VisualPosition pos, boolean fireListeners) {
    ThreadingAssertions.assertEventDispatchThread();
    checkDisposal();
    assertNotUpdating();
    if (mySkipChangeRequests) {
      return;
    }
    if (!pos.equals(myVisibleCaret)) {
      stopKillRings();
    }
    updateCachedStateIfNeeded();

    myDesiredX = -1;
    int column = pos.column;
    int line = pos.line;
    boolean leanRight = pos.leansRight;

    int lastLine = myEditor.getVisibleLineCount() - 1;
    if (line > lastLine) {
      line = lastLine;
    }

    EditorSettings editorSettings = myEditor.getSettings();

    if (!editorSettings.isVirtualSpace()) {
      int lineEndColumn = EditorUtil.getLastVisualLineColumnNumber(myEditor, line);
      if (column > lineEndColumn && !myEditor.getSoftWrapModel().isInsideSoftWrap(pos)) {
        column = lineEndColumn;
        leanRight = true;
      }
    }

    VisualPosition oldVisualPosition = myVisibleCaret;
    myVisibleCaret = new VisualPosition(line, column, leanRight);

    VerticalInfo oldVerticalInfo = myVerticalInfo;
    LogicalPosition oldPosition = myLogicalCaret;
    boolean oldInVirtualSpace = isInVirtualSpace();

    myLogicalCaret = myEditor.visualToLogicalPosition(myVisibleCaret);
    VisualPosition mappedPosition = myEditor.logicalToVisualPosition(myLogicalCaret);
    myVisualColumnAdjustment = mappedPosition.line == myVisibleCaret.line && myVisibleCaret.column > mappedPosition.column ? myVisibleCaret.column - mappedPosition.column : 0;
    updateOffsetsFromLogicalPosition();

    updateVisualLineInfo();

    myEditor.getFoldingModel().flushCaretPosition(this);

    myColumnNumberForCloning = -1;
    myDesiredSelectionStartColumn = myDesiredSelectionEndColumn = -1;
    myEditor.updateCaretCursor();
    requestRepaint(oldVerticalInfo);

    if (!oldPosition.equals(myLogicalCaret) || !oldVisualPosition.equals(myVisibleCaret)) {
      if (oldInVirtualSpace || isInVirtualSpace()) {
        myCaretModel.validateEditorSize();
      }
      if (fireListeners) {
        CaretEvent event = new CaretEvent(this, oldPosition, myLogicalCaret);
        myCaretModel.fireCaretPositionChanged(event);
      }
    }
  }

  @Nullable
  CaretEvent moveToLogicalPosition(@NotNull LogicalPosition pos,
                                           boolean locateBeforeSoftWrap,
                                           @Nullable StringBuilder debugBuffer,
                                           boolean adjustForInlays,
                                           boolean fireListeners) {
    if (mySkipChangeRequests) {
      return null;
    }
    if (!pos.equals(myLogicalCaret)) {
      stopKillRings();
    }
    return doMoveToLogicalPosition(pos, locateBeforeSoftWrap, debugBuffer, adjustForInlays, fireListeners);
  }

  private void assertNotUpdating() {
    LOG.assertTrue(
      isUpToDate() || !EDT.isCurrentThreadEdt(),
      "Caret model is in its update process. All requests are illegal at this point."
    );
  }

  @Override
  public void dispose() {
    PositionMarker positionMarker = myPositionMarker;
    if (positionMarker != null) {
      // null it first to avoid accessing invalid marker from other threads
      myPositionMarker = null;
      positionMarker.dispose();
    }
    SelectionMarker selectionMarker = mySelectionMarker;
    if (selectionMarker != null) {
      mySelectionMarker = null;
      selectionMarker.dispose();
    }
    isValid = false;
    myDisposalTrace = new Throwable();
  }

  @Override
  public boolean isUpToDate() {
    return !myCaretModel.isDocumentInUpdate();
  }

  @Override
  public @NotNull LogicalPosition getLogicalPosition() {
    assertNotUpdating();
    updateCachedStateIfNeeded();
    return myLogicalCaret;
  }

  @Override
  public @NotNull VisualPosition getVisualPosition() {
    assertNotUpdating();
    updateCachedStateIfNeeded();
    return myVisibleCaret;
  }

  @Override
  public int getOffset() {
    assertNotUpdating();
    EditorThreading.assertInteractionAllowed();
    while (true) {
      PositionMarker marker = myPositionMarker;
      if (marker == null) return 0; // caret was disposed
      int startOffset = marker.getStartOffset();
      // double-checking to avoid "concurrent dispose and return -1 from already disposed marker" race
      if (marker.isValid() && marker == myPositionMarker) return startOffset;
    }
  }

  @Override
  public int getVisualLineStart() {
    updateCachedStateIfNeeded();
    return myVisualLineStart;
  }

  @Override
  public int getVisualLineEnd() {
    updateCachedStateIfNeeded();
    return myVisualLineEnd;
  }

  void setVisualColumnAdjustment(int visualColumnAdjustment) {
    myVisualColumnAdjustment = visualColumnAdjustment;
  }

  @NotNull CaretState getCaretState() {
    LogicalPosition caret = getLogicalPosition();
    Pair<LogicalPosition, LogicalPosition> selection = getSelectionLogicalRange();
    return new CaretState(
      caret,
      myVisualColumnAdjustment,
      selection.getFirst(),
      selection.getSecond()
    );
  }

  /**
   * Recalculates caret visual position without changing its logical position (called when soft wraps are changing)
   */
  void updateVisualPosition() {
    updateCachedStateIfNeeded();
    VerticalInfo oldVerticalInfo = myVerticalInfo;
    myLogicalCaret = new LogicalPosition(myLogicalCaret.line, myLogicalCaret.column, myLogicalCaret.leansForward);
    VisualPosition visualPosition = myEditor.logicalToVisualPosition(myLogicalCaret);
    myVisibleCaret = new VisualPosition(visualPosition.line, visualPosition.column + myVisualColumnAdjustment, visualPosition.leansRight);
    updateVisualLineInfo();

    myEditor.updateCaretCursor();
    requestRepaint(oldVerticalInfo);
  }

  private void updateVisualLineInfo() {
    myVisualLineStart = myEditor.visualPositionToOffset(new VisualPosition(myVisibleCaret.line, 0));
    myVisualLineEnd = myEditor.visualPositionToOffset(new VisualPosition(myVisibleCaret.line + 1, 0));

    int[] yRange = myEditor.visualLineToYRange(myVisibleCaret.line);

    int logicalLineStartY;
    if (myEditor.getSoftWrapModel().getSoftWrap(myVisualLineStart) == null) {
      logicalLineStartY = yRange[0];
    }
    else {
      int startVisualLine = myEditor.myView.offsetToVisualLine(EditorUtil.getNotFoldedLineStartOffset(myEditor, getOffset()), false);
      logicalLineStartY = myEditor.visualLineToY(startVisualLine);
    }

    int logicalLineEndY;
    if (myEditor.getSoftWrapModel().getSoftWrap(myVisualLineEnd) == null) {
      logicalLineEndY = yRange[1];
    }
    else {
      int endVisualLine = myEditor.myView.offsetToVisualLine(EditorUtil.getNotFoldedLineEndOffset(myEditor, getOffset()), true);
      logicalLineEndY = myEditor.visualLineToY(endVisualLine) + myEditor.getLineHeight();
    }

    myVerticalInfo = new VerticalInfo(yRange[0], logicalLineStartY, logicalLineEndY - logicalLineStartY);
  }

  void onInlayAdded(int offset) {
    updateCachedStateIfNeeded();
    int currentOffset = getOffset();
    if (offset == currentOffset) {
      VisualPosition pos = EditorUtil.inlayAwareOffsetToVisualPosition(myEditor, offset);
      moveToVisualPosition(pos, false);
    }
    else {
      updateVisualPosition();
    }
  }

  void onInlayRemoved(int offset, int order) {
    int currentOffset = getOffset();
    if (offset == currentOffset && myVisualColumnAdjustment > 0 && myVisualColumnAdjustment > order) myVisualColumnAdjustment--;
    updateVisualPosition();
  }

  int getWordAtCaretStart(boolean camel) {
    Document document = myDocument;
    int offset = getOffset();
    if (offset == 0) return 0;
    int lineNumber = getLogicalPosition().line;
    int newOffset = offset - 1;
    int minOffset = lineNumber > 0 ? document.getLineEndOffset(lineNumber - 1) : 0;
    CharSequence chars = document.getImmutableCharSequence();
    for (; newOffset > minOffset; newOffset--) {
      if (EditorActionUtil.isWordOrLexemeStart(myEditor, newOffset, camel) || isBetweenBrackets(chars, newOffset)) break;
    }

    return newOffset;
  }

  int getWordAtCaretEnd(boolean camel) {
    Document document = myDocument;
    int offset = getOffset();

    if (offset >= document.getTextLength() - 1 || document.getLineCount() == 0) return offset;

    int newOffset = offset + 1;

    int lineNumber = getLogicalPosition().line;
    int maxOffset = document.getLineEndOffset(lineNumber);
    if (newOffset > maxOffset) {
      if (lineNumber + 1 >= document.getLineCount()) return offset;
      maxOffset = document.getLineEndOffset(lineNumber + 1);
    }
    CharSequence chars = document.getImmutableCharSequence();
    for (; newOffset < maxOffset; newOffset++) {
      if (EditorActionUtil.isWordOrLexemeEnd(myEditor, newOffset, camel) || isBetweenBrackets(chars, newOffset)) break;
    }

    return newOffset;
  }

  private static boolean isBetweenBrackets(CharSequence text, int offset) {
    return offset < text.length() && isBracket(text.charAt(offset)) && offset > 0 && isBracket(text.charAt(offset - 1));
  }

  private static boolean isBracket(char c) {
    return "()[]<>{}".indexOf(c) != -1;
  }

  private CaretImpl cloneWithoutSelection() {
    updateCachedStateIfNeeded();
    CaretImpl clone = new CaretImpl(myEditor, myCaretModel);
    clone.myLogicalCaret = myLogicalCaret;
    clone.myVerticalInfo = myVerticalInfo;
    clone.myVisibleCaret = myVisibleCaret;
    clone.myPositionMarker.dispose();
    clone.myPositionMarker = new PositionMarker(getOffset());
    clone.myLeansTowardsLargerOffsets = myLeansTowardsLargerOffsets;
    clone.myLogicalColumnAdjustment = myLogicalColumnAdjustment;
    clone.myVisualColumnAdjustment = myVisualColumnAdjustment;
    clone.myVisualLineStart = myVisualLineStart;
    clone.myVisualLineEnd = myVisualLineEnd;
    clone.mySkipChangeRequests = mySkipChangeRequests;
    clone.myDesiredX = myDesiredX;
    return clone;
  }

  @Override
  public @Nullable Caret clone(boolean above) {
    ThreadingAssertions.assertEventDispatchThread();
    int lineShift = above ? -1 : 1;
    LogicalPosition oldPosition = getLogicalPosition();
    int newLine = oldPosition.line + lineShift;
    if (newLine < 0 || newLine >= myDocument.getLineCount()) {
      return null;
    }
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
    int targetColumn = myColumnNumberForCloning < 0 ? oldPosition.column : myColumnNumberForCloning;
    clone.moveToLogicalPosition(new LogicalPosition(newLine, targetColumn, myLeansTowardsLargerOffsets), false, null, false, false);
    clone.myDesiredX = myDesiredX >= 0 ? myDesiredX : getCurrentX();
    clone.myDesiredSelectionStartColumn = newSelectionStartColumn;
    clone.myDesiredSelectionEndColumn = newSelectionEndColumn;
    clone.myColumnNumberForCloning = targetColumn;

    if (myCaretModel.addCaret(clone, true)) {
      if (hasNewSelection) {
        myCaretModel.doWithCaretMerging(
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
    else if (line >= myDocument.getLineCount()) {
      return myEditor.offsetToLogicalPosition(myDocument.getTextLength());
    }
    else {
      return new LogicalPosition(line, column);
    }
  }

  @Override
  public int getSelectionStart() {
    EditorThreading.assertInteractionAllowed();
    return getSelectionOffset(true);
  }

  @Override
  public int getSelectionEnd() {
    EditorThreading.assertInteractionAllowed();
    return getSelectionOffset(false);
  }

  @Override
  public @NotNull VisualPosition getSelectionStartPosition() {
    ThreadingAssertions.assertEventDispatchThread();
    return getSelectionVisualPosition(true);
  }

  @Override
  public @NotNull VisualPosition getSelectionEndPosition() {
    ThreadingAssertions.assertEventDispatchThread();
    return getSelectionVisualPosition(false);
  }

  @Override
  public boolean hasSelection() {
    EditorThreading.assertInteractionAllowed();
    SelectionMarker selectionMarker = mySelectionMarker;
    return selectionMarker != null && selectionMarker.hasSelection();
  }

  @Override
  public @NotNull TextRange getSelectionRange() {
    EditorThreading.assertInteractionAllowed();
    SelectionMarker selectionMarker = mySelectionMarker;
    if (selectionMarker != null && selectionMarker.hasSelection()) {
      return selectionMarker.getTextRange();
    }
    int offset = getOffset();
    return TextRange.create(offset, offset);
  }

  @Override
  public int getLeadSelectionOffset() {
    EditorThreading.assertInteractionAllowed();
    int caretOffset = getOffset();
    SelectionMarker selectionMarker = mySelectionMarker;
    if (selectionMarker == null || !selectionMarker.hasSelection()) {
      return caretOffset;
    }
    int startOffset = selectionMarker.getStartOffset();
    int endOffset = selectionMarker.getEndOffset();
    if (caretOffset == startOffset || caretOffset == endOffset) {
      return caretOffset == endOffset ? startOffset : endOffset;
    }
    // Try to check if current selection is tweaked by fold region.
    FoldingModelEx foldingModel = myEditor.getFoldingModel();
    FoldRegion foldRegion = foldingModel.getCollapsedRegionAtOffset(caretOffset);
    if (foldRegion != null) {
      if (foldRegion.getStartOffset() == startOffset) {
        return endOffset;
      } else if (foldRegion.getEndOffset() == endOffset) {
        return startOffset;
      }
    }
    return mySelectionEndPositionIsLead ? endOffset : startOffset;
  }

  @Override
  public @NotNull VisualPosition getLeadSelectionPosition() {
    EditorThreading.assertInteractionAllowed();
    VisualPosition pos = getLeadSelectionPositionOrNull();
    return pos != null ? pos : getVisualPosition();
  }

  @Override
  public @Nullable String getSelectedText() {
    EditorThreading.assertInteractionAllowed();
    SelectionMarker selectionMarker = mySelectionMarker;
    if (selectionMarker == null || !selectionMarker.hasSelection()) {
      return null;
    }
    TextRange selectionRange = selectionMarker.getTextRange();
    String selectedText = myDocument.getText(selectionRange);
    if (!selectionMarker.hasVirtualSelection()) {
      return selectedText;
    }
    int padding = selectionMarker.endVirtualOffset - selectionMarker.startVirtualOffset;
    StringBuilder builder = new StringBuilder(selectedText.length() + padding);
    builder.append(selectedText);
    builder.repeat(' ', padding);
    return builder.toString();
  }

  @Override
  public void setSelection(int startOffset, int endOffset) {
    setSelection(startOffset, endOffset, true);
  }

  @Override
  public void setSelection(int startOffset, int endOffset, boolean updateSystemSelection) {
    doSetSelection(
      myEditor.offsetToVisualPosition(startOffset, true, false),
      startOffset,
      myEditor.offsetToVisualPosition(endOffset, false, true),
      endOffset,
      false,
      updateSystemSelection,
      true
    );
  }

  @Override
  public void setSelection(int startOffset, @Nullable VisualPosition endPosition, int endOffset) {
    ThreadingAssertions.assertEventDispatchThread();
    VisualPosition startPosition = getLeadSelectionPositionOrNull();
    if (startPosition == null) {
      startPosition = myEditor.offsetToVisualPosition(startOffset, true, false);
    }
    setSelection(startPosition, startOffset, endPosition, endOffset);
  }

  @Override
  public void setSelection(
    @Nullable VisualPosition startPosition,
    int startOffset,
    @Nullable VisualPosition endPosition,
    int endOffset
  ) {
    setSelection(startPosition, startOffset, endPosition, endOffset, true);
  }

  @Override
  public void setSelection(
    @Nullable VisualPosition startPosition,
    int startOffset,
    @Nullable VisualPosition endPosition,
    int endOffset,
    boolean updateSystemSelection
  ) {
    VisualPosition start = startPosition != null
                           ? startPosition
                           : myEditor.offsetToVisualPosition(startOffset, true, false);
    VisualPosition end = endPosition != null
                         ? endPosition
                         : myEditor.offsetToVisualPosition(endOffset, false, true);
    doSetSelection(start, startOffset, end, endOffset, true, updateSystemSelection, true);
  }

  void doSetSelection(
    @NotNull VisualPosition startPosition,
    int _startOffset,
    @NotNull VisualPosition endPosition,
    int _endOffset,
    boolean visualPositionAware,
    boolean updateSystemSelection,
    boolean fireListeners
  ) {
    myCaretModel.doWithCaretMerging(() -> {
      int startOffset = DocumentUtil.alignToCodePointBoundary(myDocument, _startOffset);
      int endOffset = DocumentUtil.alignToCodePointBoundary(myDocument, _endOffset);
      mySelectionUnknownDirection = false;
      checkRangeBounds(startOffset, endOffset);
      if (!visualPositionAware && startOffset == endOffset) {
        removeSelection();
        return;
      }
      boolean switchedOffsets = startOffset > endOffset;
      if (switchedOffsets) {
        /* Normalize selection */
        int tmp = startOffset;
        startOffset = endOffset;
        endOffset = tmp;
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
      int oldSelectionStart, oldSelectionEnd;
      SelectionMarker oldSelectionMarker = mySelectionMarker;
      if (oldSelectionMarker != null && oldSelectionMarker.hasSelection()) {
        oldSelectionStart = oldSelectionMarker.getStartOffset();
        oldSelectionEnd = oldSelectionMarker.getEndOffset();
        if (oldSelectionStart == startOffset &&
            oldSelectionEnd == endOffset &&
            !visualPositionAware) {
          return;
        }
      } else {
        int caretOffset = getOffset();
        oldSelectionStart = caretOffset;
        oldSelectionEnd = caretOffset;
      }
      SelectionMarker newSelectionMarker = new SelectionMarker(startOffset, endOffset);
      if (!visualPositionAware) {
        mySelectionEndPositionIsLead = endOffset != getOffset();
      } else {
        if (endPosition.after(startPosition)) {
          mySelectionStartPosition = startPosition;
          mySelectionEndPosition = endPosition;
          mySelectionEndPositionIsLead = false;
        } else {
          mySelectionStartPosition = endPosition;
          mySelectionEndPosition = startPosition;
          mySelectionEndPositionIsLead = true;
        }
        if (isVirtualSelectionEnabled() &&
            myDocument.getLineNumber(startOffset) == myDocument.getLineNumber(endOffset)) {
          int endLineColumn = myEditor.offsetToVisualPosition(endOffset).column;
          int startDiff = EditorUtil.isAtLineEnd(myEditor, switchedOffsets ? endOffset : startOffset)
                          ? startPosition.column - endLineColumn
                          : 0;
          int endDiff = EditorUtil.isAtLineEnd(myEditor, switchedOffsets ? startOffset : endOffset)
                        ? endPosition.column - endLineColumn
                        : 0;
          //noinspection MathClampMigration
          newSelectionMarker.startVirtualOffset = Math.max(0, Math.min(startDiff, endDiff));
          newSelectionMarker.endVirtualOffset = Math.max(0, Math.max(startDiff, endDiff));
        }
      }
      if (oldSelectionMarker != null) {
        oldSelectionMarker.dispose();
      }
      mySelectionMarker = newSelectionMarker;
      if (fireListeners) {
        SelectionEvent event = new SelectionEvent(myEditor, oldSelectionStart, oldSelectionEnd, startOffset, endOffset);
        myEditor.getSelectionModel().fireSelectionChanged(event);
      }
      if (updateSystemSelection) {
        myCaretModel.updateSystemSelection();
      }
    });
  }

  @Override
  public void removeSelection() {
    if (myEditor.isStickySelection()) {
      // Most of our 'change caret position' actions (like move caret to word start/end etc.) remove active selection.
      // However, we don't want to do that for 'sticky selection'.
      return;
    }
    myCaretModel.doWithCaretMerging(() -> {
      mySelectionUnknownDirection = false;
      RangeMarker selectionMarker = mySelectionMarker;
      if (selectionMarker != null && selectionMarker.isValid()) {
        int startOffset = selectionMarker.getStartOffset();
        int endOffset = selectionMarker.getEndOffset();
        int caretOffset = getOffset();
        mySelectionMarker = null;
        selectionMarker.dispose();
        SelectionEvent event = new SelectionEvent(myEditor, startOffset, endOffset, caretOffset, caretOffset);
        myEditor.getSelectionModel().fireSelectionChanged(event);
      }
    });
  }

  @Override
  public void selectLineAtCaret() {
    ThreadingAssertions.assertEventDispatchThread();
    myCaretModel.doWithCaretMerging(() -> EditorActionUtil.selectEntireLines(this, true));
  }

  @Override
  public void selectWordAtCaret(boolean honorCamelWordsSettings) {
    ThreadingAssertions.assertEventDispatchThread();
    EditorActionHandler handler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CURRENT_CARET);
    DataContext context = AnActionEvent.getInjectedDataContext(EditorActionHandler.caretDataContext(myEditor.getDataContext(), this));
    DataContext customizedContext = CustomizedDataContext.withSnapshot(
      context,
      sink -> {
        sink.set(HonorCamelWordsDataContextSerializerKt.getHONOR_CAMEL_WORDS(), honorCamelWordsSettings);
      });
    Caret caret = customizedContext.getData(CommonDataKeys.CARET);
    assert caret != null;
    handler.execute(caret.getEditor(), caret, customizedContext);
  }

  @Override
  public @NotNull EditorImpl getEditor() {
    return myEditor;
  }

  @Override
  public @NonNls String toString() {
    return "Caret at " + (myDocumentUpdateCounter == myDocument.getModificationSequence() ? myVisibleCaret : getOffset()) +
           (mySelectionMarker == null ? "" : ", selection marker: " + mySelectionMarker);
  }

  @Override
  public boolean isAtRtlLocation() {
    return myEditor.myView.isRtlLocation(getVisualPosition());
  }

  @Override
  public boolean isAtBidiRunBoundary() {
    return myEditor.myView.isAtBidiRunBoundary(getVisualPosition());
  }

  @Override
  public @NotNull CaretVisualAttributes getVisualAttributes() {
    return ObjectUtils.notNull(myAttributes, CaretVisualAttributes.getDefault());
  }

  @Override
  public void setVisualAttributes(@NotNull CaretVisualAttributes attributes) {
    myAttributes = attributes == CaretVisualAttributes.getDefault() ? null : attributes;
    requestRepaint(myVerticalInfo);
  }

  @Override
  public @NotNull String dumpState() {
    return "{valid: " + isValid +
           ", update counter: " + myDocumentUpdateCounter +
           ", position: " + myPositionMarker +
           ", logical pos: " + myLogicalCaret +
           ", visual pos: " + myVisibleCaret +
           ", visual line start: " + myVisualLineStart +
           ", visual line end: " + myVisualLineEnd +
           ", skip change requests: " + mySkipChangeRequests +
           ", desired selection start column: " + myDesiredSelectionStartColumn +
           ", desired selection end column: " + myDesiredSelectionEndColumn +
           ", desired x: " + myDesiredX +
           ", selection marker: " + mySelectionMarker +
           ", rangeMarker start position: " + mySelectionStartPosition +
           ", rangeMarker end position: " + mySelectionEndPosition +
           ", rangeMarker end position is lead: " + mySelectionEndPositionIsLead +
           ", unknown direction: " + mySelectionUnknownDirection +
           ", logical column adjustment: " + myLogicalColumnAdjustment +
           ", visual column adjustment: " + myVisualColumnAdjustment + '}';
  }

  public boolean isInVirtualSpace() {
    return myLogicalColumnAdjustment > 0;
  }

  /**
   * @return  information on whether current selection's direction in known
   * @see #setUnknownDirection(boolean)
   */
  boolean isUnknownDirection() {
    return mySelectionUnknownDirection;
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
   * <b>Note:</b> when this method is called with {@code 'true'}, subsequent calls are guaranteed to return {@code true}
   * until selection is changed. 'Unknown direction' flag is automatically reset then.
   *
   */
  void setUnknownDirection(boolean unknownDirection) {
    mySelectionUnknownDirection = unknownDirection;
  }

  void resetCachedState() {
    myDocumentUpdateCounter = -1;
    myVisualColumnAdjustment = 0;
  }

  void updateCachedStateIfNeeded() {
    if (EDT.isCurrentThreadEdt()) {
      int modelCounter = myDocument.getModificationSequence();
      if (myDocumentUpdateCounter != modelCounter) {
        updateCachedState();
        myDocumentUpdateCounter = modelCounter;
      }
    }
  }

  boolean overlaps(@NotNull CaretImpl secondCaret) {
    if (getVisualPosition().equals(secondCaret.getVisualPosition())) {
      return true;
    }
    int firstStart = getSelectionStart();
    int secondStart = secondCaret.getSelectionStart();
    int firstEnd = getSelectionEnd();
    int secondEnd = secondCaret.getSelectionEnd();
    return (firstStart < secondStart && firstEnd > secondStart) ||
           (firstStart > secondStart && firstStart < secondEnd) ||
           (firstStart == secondStart && secondEnd != secondStart && firstEnd > firstStart) ||
           ((hasPureVirtualSelection() || secondCaret.hasPureVirtualSelection()) && (firstStart == secondStart || firstEnd == secondEnd));
  }

  void resetVirtualSelection() {
    SelectionMarker selectionMarker = mySelectionMarker;
    if (selectionMarker != null) {
      selectionMarker.resetVirtualSelection();
    }
  }

  private void invalidateRangeMarkerVisualPositions(@NotNull RangeMarker selectionMarker) {
    VisualPosition startVisual = mySelectionStartPosition;
    VisualPosition endVisual = mySelectionEndPosition;
    if (startVisual == null || endVisual == null) {
      mySelectionStartPosition = null;
      mySelectionEndPosition = null;
      return;
    }
    SoftWrapModel model = myEditor.getSoftWrapModel();
    InlayModel inlayModel = myEditor.getInlayModel();
    int startOffset = selectionMarker.getStartOffset();
    int endOffset = selectionMarker.getEndOffset();
    if (!myEditor.offsetToVisualPosition(startOffset, true, false).equals(startVisual) && model.getSoftWrap(startOffset) == null && !inlayModel.hasInlineElementAt(startOffset) ||
        !myEditor.offsetToVisualPosition(endOffset, false, true).equals(endVisual)     && model.getSoftWrap(endOffset) == null   && !inlayModel.hasInlineElementAt(endOffset)) {
      mySelectionStartPosition = null;
      mySelectionEndPosition = null;
    }
  }

  private int getCurrentX() {
    return myEditor.visualPositionToXY(myVisibleCaret).x;
  }

  private void updateCachedState() {
    int caretOffset = getOffset();
    int logicalColumnAdjustment = myLogicalColumnAdjustment;
    boolean leansTowardsLargerOffsets = myLeansTowardsLargerOffsets;
    LogicalPosition lp = myEditor.offsetToLogicalPosition(caretOffset);
    myLogicalCaret = new LogicalPosition(lp.line, lp.column + logicalColumnAdjustment, leansTowardsLargerOffsets);
    VisualPosition visualPosition = myEditor.logicalToVisualPosition(myLogicalCaret);
    myVisibleCaret = new VisualPosition(visualPosition.line, visualPosition.column + myVisualColumnAdjustment, visualPosition.leansRight);
    updateVisualLineInfo();
    myColumnNumberForCloning = -1;
    myDesiredSelectionStartColumn = -1;
    myDesiredSelectionEndColumn = -1;
    myDesiredX = -1;
  }

  private void checkDisposal() {
    if (myEditor.isDisposed()) {
      myEditor.throwDisposalError("Editor is already disposed");
    }
    if (!isValid) {
      throw new IllegalStateException("Caret is invalid", myDisposalTrace);
    }
  }

  private void stopKillRings() {
    if (!myEditor.isStickySelection() && !myDocument.isInEventsHandling()) {
      CopyPasteManager.getInstance().stopKillRings(myDocument);
    }
  }

  private int getSelectionOffset(boolean isStart) {
    SelectionMarker selectionMarker = mySelectionMarker;
    if (selectionMarker != null && selectionMarker.hasSelection()) {
      return isStart
             ? selectionMarker.getStartOffset()
             : selectionMarker.getEndOffset();
    }
    return getOffset();
  }

  private @NotNull VisualPosition getSelectionVisualPosition(boolean isStart) {
    SelectionMarker selectionMarker = mySelectionMarker;
    if (selectionMarker != null && selectionMarker.hasSelection()) {
      return getSelectionVisualPosition(selectionMarker, isStart);
    }
    return isVirtualSelectionEnabled()
           ? getVisualPosition()
           : myEditor.offsetToVisualPosition(getOffset(), getLogicalPosition().leansForward, false);
  }

  private @Nullable VisualPosition getLeadSelectionPositionOrNull() {
    SelectionMarker selectionMarker = mySelectionMarker;
    if (selectionMarker != null && selectionMarker.hasSelection()) {
      return getSelectionVisualPosition(selectionMarker, !mySelectionEndPositionIsLead);
    }
    return null;
  }

  private @NotNull VisualPosition getSelectionVisualPosition(@NotNull SelectionMarker selectionMarker, boolean isStart) {
    invalidateRangeMarkerVisualPositions(selectionMarker);
    VisualPosition position = isStart ? mySelectionStartPosition : mySelectionEndPosition;
    if (position == null) {
      VisualPosition startPosition = myEditor.offsetToVisualPosition(selectionMarker.getStartOffset(), true, false);
      VisualPosition endPosition = myEditor.offsetToVisualPosition(selectionMarker.getEndOffset(), false, true);
      if (isStart) {
        position = startPosition.after(endPosition) ? endPosition : startPosition;
      } else {
        position = startPosition.after(endPosition) ? startPosition : endPosition;
      }
    }
    if (!selectionMarker.hasVirtualSelection()) {
      return position;
    }
    int virtualColumn = isStart ? selectionMarker.startVirtualOffset :  selectionMarker.endVirtualOffset;
    return new VisualPosition(position.line, position.column + virtualColumn);
  }

  private @NotNull Pair<LogicalPosition, LogicalPosition> getSelectionLogicalRange() {
    ThreadingAssertions.assertEventDispatchThread();
    SelectionMarker selectionMarker = mySelectionMarker;
    if (selectionMarker == null || !selectionMarker.hasSelection()) {
      LogicalPosition caretPos = getLogicalPosition();
      return new Pair<>(caretPos, caretPos);
    }
    invalidateRangeMarkerVisualPositions(selectionMarker);
    VisualPosition visualStart = mySelectionStartPosition;
    VisualPosition visualEnd = mySelectionEndPosition;
    LogicalPosition startPos = visualStart != null
               ? myEditor.visualToLogicalPosition(visualStart)
               : myEditor.offsetToLogicalPosition(selectionMarker.getStartOffset()).leanForward(true);
    LogicalPosition endPos = visualEnd != null
             ? myEditor.visualToLogicalPosition(visualEnd)
             : myEditor.offsetToLogicalPosition(selectionMarker.getEndOffset());
    if (selectionMarker.hasVirtualSelection()) {
      startPos = new LogicalPosition(startPos.line, startPos.column + selectionMarker.startVirtualOffset);
      endPos = new LogicalPosition(endPos.line, endPos.column + selectionMarker.endVirtualOffset);
    }
    return new Pair<>(startPos, endPos);
  }

  private boolean hasPureVirtualSelection() {
    SelectionMarker selectionMarker = mySelectionMarker;
    return selectionMarker != null && selectionMarker.hasPureVirtualSelection();
  }

  private boolean isVirtualSelectionEnabled() {
    return myEditor.isColumnMode();
  }

  private void checkRangeBounds(int startOffset, int endOffset) {
    int textLength = myDocument.getTextLength();
    if (startOffset < 0 || startOffset > textLength) {
      LOG.error("Wrong startOffset: " + startOffset + ", textLength=" + textLength);
    }
    if (endOffset < 0 || endOffset > textLength) {
      LOG.error("Wrong endOffset: " + endOffset + ", textLength=" + textLength);
    }
  }

  private static int getInitialVisualLineEnd(@NotNull Document document) {
    int lineCount = document.getLineCount();
    if (lineCount == 0) {
      return 0;
    }
    if (lineCount == 1) {
      return document.getLineEndOffset(0);
    }
    return document.getLineStartOffset(1);
  }

  @TestOnly
  public void validateState() {
    LOG.assertTrue(!DocumentUtil.isInsideSurrogatePair(myDocument, getOffset()));
    LOG.assertTrue(!DocumentUtil.isInsideSurrogatePair(myDocument, getSelectionStart()));
    LOG.assertTrue(!DocumentUtil.isInsideSurrogatePair(myDocument, getSelectionEnd()));
  }

  final class PositionMarker extends RangeMarkerImpl {
    private PositionMarker(int offset) {
      super(myDocument, offset, offset, false, true);
      //noinspection SuspiciousPackagePrivateAccess
      myCaretModel.getPositionMarkerTree().addInterval(this, offset, offset, false, false, false, 0);
    }

    @Override
    public void dispose() {
      if (isValid()) {
        myCaretModel.getPositionMarkerTree().removeInterval(this);
      }
    }

    @Override
    protected void changedUpdateImpl(@NotNull DocumentEvent e) {
      int oldOffset = intervalStart();
      //noinspection SuspiciousPackagePrivateAccess
      RangeMarkerTree.RMNode<RangeMarkerEx> node = myNode;
      //noinspection SuspiciousPackagePrivateAccess
      long newRange = isValid() && node != null ? applyChange(e, node.toScalarRange(), isGreedyToLeft(), isGreedyToRight(), isStickingToRight()) : -1;

      if (newRange != -1) {
        setRange(newRange);
        // Under certain conditions, when text is inserted at caret position, we position caret at the end of inserted text.
        // Ideally, client code should be responsible for positioning caret after document modification, but in case of
        // postponed formatting (after PSI modifications), this is hard to implement, so a heuristic below is used.
        if (e.getOldLength() == 0 && oldOffset == e.getOffset() &&
            !Boolean.TRUE.equals(myEditor.getUserData(EditorImpl.DISABLE_CARET_SHIFT_ON_WHITESPACE_INSERTION)) &&
            needToShiftWhiteSpaces(e)) {
          int afterInserted = e.getOffset() + e.getNewLength();
          setRange(TextRangeScalarUtil.toScalarRange(afterInserted, afterInserted));
        }
        int offset = intervalStart();
        if (DocumentUtil.isInsideSurrogatePair(getDocument(), offset)) {
          setRange(TextRangeScalarUtil.toScalarRange(offset - 1, offset - 1));
        }
      }
      else {
        setValid(true);
        int newOffset = Math.min(getStartOffset(), e.getOffset() + e.getNewLength());
        if (!e.getDocument().isInBulkUpdate() && e.isWholeTextReplaced()) {
          try {
            int line = ((DocumentEventImpl)e).translateLineViaDiff(myLogicalCaret.line);
            newOffset = myEditor.logicalPositionToOffset(new LogicalPosition(line, myLogicalCaret.column));
          }
          catch (FilesTooBigForDiffException ex) {
            LOG.info(ex);
          }
        }
        newOffset = DocumentUtil.alignToCodePointBoundary(getDocument(), newOffset);
        setRange(TextRangeScalarUtil.toScalarRange(newOffset, newOffset));
      }
      myLogicalColumnAdjustment = 0;
      myVisualColumnAdjustment = 0;
      if (oldOffset >= e.getOffset() && oldOffset <= e.getOffset() + e.getOldLength() && e.getNewLength() == 0 && e.getOldLength() > 0) {
        int inlaysToTheLeft = myEditor.getInlayModel().getInlineElementsInRange(e.getOffset(), e.getOffset()).size();
        boolean hasInlaysToTheRight = myEditor.getInlayModel().hasInlineElementAt(e.getOffset() + e.getOldLength());
        if (inlaysToTheLeft > 0 || hasInlaysToTheRight) {
          myLeansTowardsLargerOffsets = !hasInlaysToTheRight;
          myVisualColumnAdjustment = hasInlaysToTheRight ? inlaysToTheLeft : 0;
        }
        else if (oldOffset == e.getOffset()) {
          myLeansTowardsLargerOffsets = false;
        }
      }
    }

    @Override
    protected void onReTarget(@NotNull DocumentEvent e) {
      int offset = intervalStart();
      if (DocumentUtil.isInsideSurrogatePair(getDocument(), offset)) {
        setRange(TextRangeScalarUtil.toScalarRange(offset - 1, offset - 1));
      }
    }

    private static boolean needToShiftWhiteSpaces(@NotNull DocumentEvent e) {
      return e.getOffset() > 0 &&
             Character.isWhitespace(e.getDocument().getImmutableCharSequence().charAt(e.getOffset() - 1)) &&
             CharArrayUtil.containsOnlyWhiteSpaces(e.getNewFragment()) &&
             !CharArrayUtil.containLineBreaks(e.getNewFragment());
    }
  }

  final class SelectionMarker extends RangeMarkerImpl {
    // offsets of selection start/end position relative to end of line - can be non-zero in column selection mode
    // these are non-negative values, myStartVirtualOffset is always less or equal to myEndVirtualOffset
    private int startVirtualOffset;
    private int endVirtualOffset;

    SelectionMarker(int start, int end) {
      super(myDocument, start, end, false, true);
      //noinspection SuspiciousPackagePrivateAccess
      myCaretModel.getSelectionMarkerTree().addInterval(this, start, end, false, false, false, 0);
    }

    boolean hasSelection() {
      return isValid() &&
             (getStartOffset() < getEndOffset() || hasVirtualSelection());
    }

    boolean hasVirtualSelection() {
      return isValid() &&
             isVirtualSelectionEnabled() &&
             startVirtualOffset < endVirtualOffset;
    }

    boolean hasPureVirtualSelection() {
      return isValid() &&
             getStartOffset() == getEndOffset() &&
             hasVirtualSelection();
    }

    void resetVirtualSelection() {
      startVirtualOffset = 0;
      endVirtualOffset = 0;
    }

    @Override
    public void dispose() {
      if (isValid()) {
        myCaretModel.getSelectionMarkerTree().removeInterval(this);
      }
    }

    @Override
    protected void changedUpdateImpl(@NotNull DocumentEvent e) {
      super.changedUpdateImpl(e);
      if (isValid()) {
        alignToSurrogatePairBoundaries();
      }
      if (endVirtualOffset > 0 && isValid()) {
        Document document = e.getDocument();
        int startAfter = intervalStart();
        int endAfter = intervalEnd();
        if (!DocumentUtil.isAtLineEnd(endAfter, document) ||
            document.getLineNumber(startAfter) != document.getLineNumber(endAfter)) {
          resetVirtualSelection();
        }
      }
    }

    @Override
    protected void onReTarget(@NotNull DocumentEvent e) {
      alignToSurrogatePairBoundaries();
    }

    private void alignToSurrogatePairBoundaries() {
      long alignedRange = TextRangeScalarUtil.shift(
        toScalarRange(),
        DocumentUtil.isInsideSurrogatePair(getDocument(), getStartOffset()) ? -1 : 0,
        DocumentUtil.isInsideSurrogatePair(getDocument(), getEndOffset()) ? -1 : 0
      );
      setRange(alignedRange);
    }

    @Override
    public String toString() {
      return super.toString() +
             (endVirtualOffset > startVirtualOffset
              ? " virtual selection: " + startVirtualOffset + "-" + endVirtualOffset
              : "");
    }
  }

  // IDEA-205802 Repaint issues for soft-wrap marks in a diff pane
  private record VerticalInfo(
    int y, // y coordinate of caret
    int logicalLineY, // y coordinate of caret's logical line start
    int logicalLineHeight // height of caret's logical line
                          // (If there are soft wraps, it's larger than a visual line's height.
                          // it's also larger if caret is located at a custom fold region)
  ) {
  }
}
