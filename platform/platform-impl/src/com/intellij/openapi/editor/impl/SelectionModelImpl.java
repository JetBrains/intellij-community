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
 * Date: Apr 19, 2002
 * Time: 1:51:41 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyClipboardOwner;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.List;

public class SelectionModelImpl implements SelectionModel, PrioritizedDocumentListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.SelectionModelImpl");

  private final List<SelectionListener> mySelectionListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private volatile MyRangeMarker mySelectionMarker;
  private final EditorImpl myEditor;
  private int myLastSelectionStart;
  private LogicalPosition myBlockStart;
  private LogicalPosition myBlockEnd;
  private TextAttributes myTextAttributes;
  private DocumentEvent myIsInUpdate;
  private int[] myBlockSelectionStarts;
  private int[] myBlockSelectionEnds;
  private boolean myUnknownDirection;

  private class MyRangeMarker extends RangeMarkerImpl {
    private VisualPosition myStartPosition;
    private VisualPosition myEndPosition;
    private boolean myEndPositionIsLead;
    private boolean myIsReleased;

    MyRangeMarker(DocumentEx document, int start, int end) {
      super(document, start, end, true);
      myIsReleased = false;
    }

    public void release() {
      myIsReleased = true;
      dispose();
    }

    @Nullable
    public VisualPosition getStartPosition() {
      invalidateVisualPositions();
      return myStartPosition;
    }

    public void setStartPosition(@NotNull VisualPosition startPosition) {
      myStartPosition = startPosition;
    }

    @Nullable
    public VisualPosition getEndPosition() {
      invalidateVisualPositions();
      return myEndPosition;
    }

    public void setEndPosition(@NotNull VisualPosition endPosition) {
      myEndPosition = endPosition;
    }

    public boolean isEndPositionIsLead() {
      return myEndPositionIsLead;
    }

    public void setEndPositionIsLead(boolean endPositionIsLead) {
      myEndPositionIsLead = endPositionIsLead;
    }

    int startBefore;
    int endBefore;

    @Override
    protected void changedUpdateImpl(DocumentEvent e) {
      if (myIsReleased) return;
      startBefore = getStartOffset();
      endBefore = getEndOffset();
      super.changedUpdateImpl(e);
    }

    private void invalidateVisualPositions() {
      SoftWrapModelImpl model = myEditor.getSoftWrapModel();
      if (!myEditor.offsetToVisualPosition(getStartOffset()).equals(myStartPosition) && model.getSoftWrap(getStartOffset()) == null
          || !myEditor.offsetToVisualPosition(getEndOffset()).equals(myEndPosition) && model.getSoftWrap(getEndOffset()) == null) {
        myStartPosition = null;
        myEndPosition = null;
      }
    }
  }

  private int startBefore;
  private int endBefore;

  @Override
  public void beforeDocumentChange(DocumentEvent event) {
    myIsInUpdate = event;
    MyRangeMarker marker = mySelectionMarker;
    if (marker != null && marker.isValid()) {
      startBefore = marker.getStartOffset();
      endBefore = marker.getEndOffset();
    }
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    if (myIsInUpdate == event) {
      myIsInUpdate = null;
      MyRangeMarker marker = mySelectionMarker;
      if (marker != null) {
        int endAfter;
        int startAfter;
        if (marker.isValid()) {
          startAfter = marker.getStartOffset();
          endAfter = marker.getEndOffset();
        }
        else {
          myLastSelectionStart = myEditor.getCaretModel().getOffset();
          marker.release();
          mySelectionMarker = null;
          startAfter = endAfter = myLastSelectionStart;
        }

        if (startBefore != startAfter || endBefore != endAfter) {
          fireSelectionChanged(startBefore, endBefore, startAfter, endAfter);
        }
      }
    }
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.SELECTION_MODEL;
  }

  public SelectionModelImpl(EditorImpl editor) {
    myEditor = editor;
  }

  @Override
  public int getSelectionStart() {
    validateContext(false);
    if (hasSelection()) {
      MyRangeMarker marker = mySelectionMarker;
      if (marker != null) {
        return marker.getStartOffset();
      }
    }
    return myEditor.getCaretModel().getOffset();
  }

  @NotNull
  @Override
  public VisualPosition getSelectionStartPosition() {
    VisualPosition defaultPosition = myEditor.offsetToVisualPosition(getSelectionStart());
    if (!hasSelection()) {
      return defaultPosition;
    }

    MyRangeMarker marker = mySelectionMarker;
    if (marker == null) {
      return defaultPosition;
    }

    VisualPosition result = marker.getStartPosition();
    return result == null ? defaultPosition : result;
  }

  private void validateContext(boolean isWrite) {

    if (!myEditor.getComponent().isShowing()) return;
    if (isWrite) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }
    else {
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }

    //if (myIsInUpdate != null) {
    //  documentChanged(myIsInUpdate);
    //}
  }

  /**
   * @return  information on whether current selection's direction in known
   * @see #setUnknownDirection(boolean) 
   */
  public boolean isUnknownDirection() {
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
   * @param unknownDirection
   */
  public void setUnknownDirection(boolean unknownDirection) {
    myUnknownDirection = unknownDirection;
  }

  @Override
  public int getSelectionEnd() {
    validateContext(false);
    if (hasSelection()) {
      MyRangeMarker marker = mySelectionMarker;
      if (marker != null) {
        return marker.getEndOffset();
      }
    }
    return myEditor.getCaretModel().getOffset();
  }

  @NotNull
  @Override
  public VisualPosition getSelectionEndPosition() {
    VisualPosition defaultPosition = myEditor.offsetToVisualPosition(getSelectionEnd());
    if (!hasSelection()) {
      return defaultPosition;
    }

    MyRangeMarker marker = mySelectionMarker;
    if (marker == null) {
      return defaultPosition;
    }

    VisualPosition result = marker.getEndPosition();
    return result == null ? defaultPosition : result;
  }

  @Override
  public boolean hasSelection() {
    validateContext(false);
    MyRangeMarker marker = mySelectionMarker;
    //if (marker != null && !marker.isValid()) {
    //  removeSelection();
    //}

    return marker != null && marker.isValid() && marker.getEndOffset() > marker.getStartOffset();
  }

  @Override
  public void setSelection(int startOffset, int endOffset) {
    doSetSelection(myEditor.offsetToVisualPosition(startOffset), startOffset, myEditor.offsetToVisualPosition(endOffset), endOffset, false);
  }

  @Override
  public void setSelection(int startOffset, @Nullable VisualPosition endPosition, int endOffset) {
    VisualPosition startPosition;
    if (hasSelection()) {
      startPosition = getLeadSelectionPosition();
    }
    else {
      startPosition = myEditor.offsetToVisualPosition(startOffset);
    }
    setSelection(startPosition, startOffset, endPosition, endOffset);
  }

  @Override
  public void setSelection(@Nullable VisualPosition startPosition, int startOffset, @Nullable VisualPosition endPosition, int endOffset) {
    VisualPosition startPositionToUse = startPosition == null ? myEditor.offsetToVisualPosition(startOffset) : startPosition;
    VisualPosition endPositionToUse = endPosition == null ? myEditor.offsetToVisualPosition(endOffset) : endPosition;
    doSetSelection(startPositionToUse, startOffset, endPositionToUse, endOffset, true);
  }

  private void doSetSelection(@NotNull VisualPosition startPosition,
                              int startOffset,
                              @NotNull VisualPosition endPosition,
                              int endOffset,
                              boolean visualPositionAware)
  {
    myUnknownDirection = false;
    final Document doc = myEditor.getDocument();
    final Pair<String, String> markers = myEditor.getUserData(EditorImpl.EDITABLE_AREA_MARKER);
    if (markers != null) {
      final String text = doc.getText();
      final int start = text.indexOf(markers.first) + markers.first.length();
      final int end = text.indexOf(markers.second);
      if (startOffset < endOffset) {
        if (startOffset < start) {
          startOffset = start;
          startPosition = myEditor.offsetToVisualPosition(startOffset);
        }
        if (endOffset > end) {
          endOffset = end;
          endPosition = myEditor.offsetToVisualPosition(endOffset);
        }
      }
      else {
        if (endOffset < start) {
          endOffset = start;
          endPosition = myEditor.offsetToVisualPosition(startOffset);
        }
        if (startOffset > end) {
          startOffset = end;
          startPosition = myEditor.offsetToVisualPosition(endOffset);
        }
      }
    }

    validateContext(true);

    removeBlockSelection();

    int textLength = doc.getTextLength();
    if (startOffset < 0 || startOffset > textLength) {
      LOG.error("Wrong startOffset: " + startOffset + ", textLength=" + textLength);
    }
    if (endOffset < 0 || endOffset > textLength) {
      LOG.error("Wrong endOffset: " + endOffset + ", textLength=" + textLength);
    }

    myLastSelectionStart = startOffset;
    if (!visualPositionAware && startOffset == endOffset) {
      removeSelection();
      return;
    }

    /* Normalize selection */
    if (startOffset > endOffset) {
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

    int oldSelectionStart;
    int oldSelectionEnd;

    if (hasSelection()) {
      oldSelectionStart = getSelectionStart();
      oldSelectionEnd = getSelectionEnd();
      if (oldSelectionStart == startOffset && oldSelectionEnd == endOffset && !visualPositionAware) return;
    }
    else {
      oldSelectionStart = oldSelectionEnd = myEditor.getCaretModel().getOffset();
    }

    MyRangeMarker marker = mySelectionMarker;
    if (marker != null) {
      marker.release();
    }

    marker = new MyRangeMarker((DocumentEx)doc, startOffset, endOffset);
    if (visualPositionAware) {
      if (endPosition.after(startPosition)) {
        marker.setStartPosition(startPosition);
        marker.setEndPosition(endPosition);
        marker.setEndPositionIsLead(false);
      }
      else {
        marker.setStartPosition(endPosition);
        marker.setEndPosition(startPosition);
        marker.setEndPositionIsLead(true);
      }
    }
    mySelectionMarker = marker;

    fireSelectionChanged(oldSelectionStart, oldSelectionEnd, startOffset, endOffset);

    updateSystemSelection();
  }

  private void updateSystemSelection() {
    if (GraphicsEnvironment.isHeadless()) return;

    final Clipboard clip = myEditor.getComponent().getToolkit().getSystemSelection();
    if (clip != null) {
      clip.setContents(new StringSelection(getSelectedText()), EmptyClipboardOwner.INSTANCE);
    }
  }

  private void fireSelectionChanged(int oldSelectionStart, int oldSelectionEnd, int startOffset, int endOffset) {
    repaintBySelectionChange(oldSelectionStart, startOffset, oldSelectionEnd, endOffset);

    SelectionEvent event = new SelectionEvent(myEditor,
                                              oldSelectionStart, oldSelectionEnd,
                                              startOffset, endOffset);

    broadcastSelectionEvent(event);
  }

  private void broadcastSelectionEvent(SelectionEvent event) {
    for (SelectionListener listener : mySelectionListeners) {
      try {
        listener.selectionChanged(event);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  private void repaintBySelectionChange(int oldSelectionStart, int startOffset, int oldSelectionEnd, int endOffset) {
    myEditor.repaint(Math.min(oldSelectionStart, startOffset), Math.max(oldSelectionStart, startOffset));
    myEditor.repaint(Math.min(oldSelectionEnd, endOffset), Math.max(oldSelectionEnd, endOffset));
  }

  @Override
  public void setBlockSelection(LogicalPosition blockStart, LogicalPosition blockEnd) {
    removeSelection();

    int oldStartLine = 0;
    int oldEndLine = 0;

    if (hasBlockSelection()) {
      oldStartLine = myBlockStart.line;
      oldEndLine = myBlockEnd.line;
      if (oldStartLine > oldEndLine) {
        int t = oldStartLine;
        oldStartLine = oldEndLine;
        oldEndLine = t;
      }
    }

    int newStartLine = blockStart.line;
    int newEndLine = blockEnd.line;

    if (newStartLine > newEndLine) {
      int t = newStartLine;
      newStartLine = newEndLine;
      newEndLine = t;
    }

    myEditor.repaintLines(Math.min(oldStartLine, newStartLine), Math.max(newEndLine, oldEndLine));

    final int[] oldStarts = getBlockSelectionStarts();
    final int[] oldEnds = getBlockSelectionEnds();

    myBlockStart = blockStart;
    myBlockEnd = blockEnd;
    recalculateBlockOffsets();

    final int[] newStarts = getBlockSelectionStarts();
    final int[] newEnds = getBlockSelectionEnds();

    broadcastSelectionEvent(new SelectionEvent(myEditor, oldStarts, oldEnds, newStarts, newEnds));
  }

  @Override
  public void removeSelection() {
    if (myEditor.isStickySelection()) {
      // Most of our 'change caret position' actions (like move caret to word start/end etc) remove active selection.
      // However, we don't want to do that for 'sticky selection'.
      return;
    }
    validateContext(true);
    removeBlockSelection();
    myLastSelectionStart = myEditor.getCaretModel().getOffset();
    MyRangeMarker marker = mySelectionMarker;
    if (marker != null) {
      int startOffset = marker.getStartOffset();
      int endOffset = marker.getEndOffset();
      marker.release();
      mySelectionMarker = null;
      fireSelectionChanged(startOffset, endOffset, myLastSelectionStart, myLastSelectionStart);
    }
  }

  @Override
  public void removeBlockSelection() {
    myUnknownDirection = false;
    if (hasBlockSelection()) {
      myEditor.repaint(0, myEditor.getDocument().getTextLength());

      final int[] oldStarts = getBlockSelectionStarts();
      final int[] oldEnds = getBlockSelectionEnds();

      myBlockStart = null;
      myBlockEnd = null;

      final int[] newStarts = getBlockSelectionStarts();
      final int[] newEnds = getBlockSelectionEnds();

      broadcastSelectionEvent(new SelectionEvent(myEditor, oldStarts, oldEnds, newStarts, newEnds));
    }
  }

  @Override
  public boolean hasBlockSelection() {
    return myBlockStart != null;
  }

  @Override
  public LogicalPosition getBlockStart() {
    return myBlockStart;
  }

  @Override
  public LogicalPosition getBlockEnd() {
    return myBlockEnd;
  }

  @Override
  public boolean isBlockSelectionGuarded() {
    if (!hasBlockSelection()) return false;
    int[] starts = getBlockSelectionStarts();
    int[] ends = getBlockSelectionEnds();
    Document doc = myEditor.getDocument();
    for (int i = 0; i < starts.length; i++) {
      int start = starts[i];
      int end = ends[i];
      if (start == end && doc.getOffsetGuard(start) != null || start != end && doc.getRangeGuard(start, end) != null) {
        return true;
      }
    }
    return false;
  }

  @Override
  public RangeMarker getBlockSelectionGuard() {
    if (!hasBlockSelection()) return null;

    int[] starts = getBlockSelectionStarts();
    int[] ends = getBlockSelectionEnds();
    Document doc = myEditor.getDocument();
    for (int i = 0; i < starts.length; i++) {
      int start = starts[i];
      int end = ends[i];
      if (start == end) {
        RangeMarker guard = doc.getOffsetGuard(start);
        if (guard != null) return guard;
      }
      if (start != end) {
        RangeMarker guard = doc.getRangeGuard(start, end);
        if (guard != null) return guard;
      }
    }

    return null;
  }

  private void recalculateBlockOffsets() {
    TIntArrayList startOffsets = new TIntArrayList();
    TIntArrayList endOffsets = new TIntArrayList();
    final int startLine = Math.min(myBlockStart.line, myBlockEnd.line);
    final int endLine = Math.max(myBlockStart.line, myBlockEnd.line);
    final int startColumn = Math.min(myBlockStart.column, myBlockEnd.column);
    final int endColumn = Math.max(myBlockStart.column, myBlockEnd.column);
    FoldingModelImpl foldingModel = myEditor.getFoldingModel();
    DocumentEx document = myEditor.getDocument();
    boolean insideFoldRegion = false;
    for (int line = startLine; line <= endLine; line++) {
      int startOffset = myEditor.logicalPositionToOffset(new LogicalPosition(line, startColumn));
      FoldRegion startRegion = foldingModel.getCollapsedRegionAtOffset(startOffset);
      boolean startInsideFold = startRegion != null && startRegion.getStartOffset() < startOffset;

      int endOffset = myEditor.logicalPositionToOffset(new LogicalPosition(line, endColumn));
      FoldRegion endRegion = foldingModel.getCollapsedRegionAtOffset(endOffset);
      boolean endInsideFold = endRegion != null && endRegion.getStartOffset() < endOffset;

      if (!startInsideFold && !endInsideFold) {
        startOffsets.add(startOffset);
        endOffsets.add(endOffset);
      }
      else if (startInsideFold && endInsideFold) {
        if (insideFoldRegion) {
          startOffsets.add(Math.max(document.getLineStartOffset(line), startRegion.getStartOffset()));
          endOffsets.add(Math.min(document.getLineEndOffset(line), endRegion.getEndOffset()));
        }
      }
      else if (startInsideFold && !endInsideFold) {
        if (startRegion.getEndOffset() < endOffset) {
          startOffsets.add(Math.max(document.getLineStartOffset(line), startRegion.getStartOffset()));
          endOffsets.add(endOffset);
        }
        insideFoldRegion = false;
      }
      else {
        startOffsets.add(startOffset);
        endOffsets.add(Math.min(document.getLineEndOffset(line), endRegion.getEndOffset()));
        insideFoldRegion = true;
      }
    }

    myBlockSelectionStarts = startOffsets.toNativeArray();
    myBlockSelectionEnds = endOffsets.toNativeArray();
  }

  @Override
  @NotNull
  public int[] getBlockSelectionStarts() {
    if (hasSelection()) {
      return new int[]{getSelectionStart()};
    }
    else if (!hasBlockSelection() || myBlockSelectionStarts == null) {
      return ArrayUtil.EMPTY_INT_ARRAY;
    }
    else {
      return myBlockSelectionStarts;
    }
  }

  @Override
  @NotNull
  public int[] getBlockSelectionEnds() {
    if (hasSelection()) {
      return new int[]{getSelectionEnd()};
    }
    else if (!hasBlockSelection() || myBlockSelectionEnds == null) {
      return ArrayUtil.EMPTY_INT_ARRAY;
    }
    else {
      return myBlockSelectionEnds;
    }
  }

  @Override
  public void addSelectionListener(SelectionListener listener) {
    mySelectionListeners.add(listener);
  }

  @Override
  public void removeSelectionListener(SelectionListener listener) {
    boolean success = mySelectionListeners.remove(listener);
    LOG.assertTrue(success);
  }

  @Override
  public String getSelectedText() {
    validateContext(false);
    if (!hasSelection() && !hasBlockSelection()) return null;

    CharSequence text = myEditor.getDocument().getCharsSequence();
    if (hasBlockSelection()) {
      int[] starts = getBlockSelectionStarts();
      int[] ends = getBlockSelectionEnds();
      int width = Math.abs(myBlockEnd.column - myBlockStart.column);
      final StringBuilder buf = new StringBuilder();
      for (int i = 0; i < starts.length; i++) {
        if (i > 0) buf.append('\n');
        final int len = ends[i] - starts[i];
        appendCharSequence(buf, text, starts[i], len);
        for (int j = len; j < width; j++) buf.append(' ');
      }
      return buf.toString();
    }

    int selectionStart = getSelectionStart();
    int selectionEnd = getSelectionEnd();
    return text.subSequence(selectionStart, selectionEnd).toString();
  }

  private static void appendCharSequence(@NotNull StringBuilder buf, @NotNull CharSequence s, int srcOffset, int len) {
    if (srcOffset < 0 || len < 0 || srcOffset > s.length() - len) {
      throw new IndexOutOfBoundsException("srcOffset " + srcOffset + ", len " + len + ", s.length() " + s.length());
    }
    if (len == 0) {
      return;
    }
    final int limit = srcOffset + len;
    for (int i = srcOffset; i < limit; i++) {
      buf.append(s.charAt(i));
    }
  }

  @Override
  public int getLeadSelectionOffset() {
    validateContext(false);
    int caretOffset = myEditor.getCaretModel().getOffset();
    if (hasSelection()) {
      MyRangeMarker marker = mySelectionMarker;
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
    MyRangeMarker marker = mySelectionMarker;
    VisualPosition caretPosition = myEditor.getCaretModel().getVisualPosition();
    if (marker == null) {
      return caretPosition;
    }

    if (marker.isEndPositionIsLead()) {
      VisualPosition result = marker.getEndPosition();
      return result == null ? getSelectionEndPosition() : result;
    }
    else {
      VisualPosition result = marker.getStartPosition();
      return result == null ? getSelectionStartPosition() : result;
    }
  }

  @Override
  public void selectLineAtCaret() {
    validateContext(true);
    doSelectLineAtCaret(myEditor);
  }

  public static void doSelectLineAtCaret(Editor editor) {
    int lineNumber = editor.getCaretModel().getLogicalPosition().line;
    Document document = editor.getDocument();
    if (lineNumber >= document.getLineCount()) {
      return;
    }

    Pair<LogicalPosition, LogicalPosition> lines = EditorUtil.calcCaretLineRange(editor);
    LogicalPosition lineStart = lines.first;
    LogicalPosition nextLineStart = lines.second;

    int start = editor.logicalPositionToOffset(lineStart);
    int end = editor.logicalPositionToOffset(nextLineStart);

    //myEditor.getCaretModel().moveToOffset(start);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().removeSelection();
    editor.getSelectionModel().setSelection(start, end);
  }

  @Override
  public void selectWordAtCaret(boolean honorCamelWordsSettings) {
    validateContext(true);
    removeSelection();
    final EditorSettings settings = myEditor.getSettings();
    boolean camelTemp = settings.isCamelWords();

    final boolean needOverrideSetting = camelTemp && !honorCamelWordsSettings;
    if (needOverrideSetting) {
      settings.setCamelWords(false);
    }

    try {
      EditorActionHandler handler = EditorActionManager.getInstance().getActionHandler(
        IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
      handler.execute(myEditor, myEditor.getDataContext());
    }
    finally {
      if (needOverrideSetting) {
        settings.resetCamelWords();
      }
    }
  }

  int getWordAtCaretStart() {
    Document document = myEditor.getDocument();
    int offset = myEditor.getCaretModel().getOffset();
    if (offset == 0) return 0;
    int lineNumber = myEditor.getCaretModel().getLogicalPosition().line;
    CharSequence text = document.getCharsSequence();
    int newOffset = offset - 1;
    int minOffset = lineNumber > 0 ? document.getLineEndOffset(lineNumber - 1) : 0;
    boolean camel = myEditor.getSettings().isCamelWords();
    for (; newOffset > minOffset; newOffset--) {
      if (EditorActionUtil.isWordStart(text, newOffset, camel)) break;
    }

    return newOffset;
  }

  int getWordAtCaretEnd() {
    Document document = myEditor.getDocument();
    int offset = myEditor.getCaretModel().getOffset();

    CharSequence text = document.getCharsSequence();
    if (offset >= document.getTextLength() - 1 || document.getLineCount() == 0) return offset;

    int newOffset = offset + 1;

    int lineNumber = myEditor.getCaretModel().getLogicalPosition().line;
    int maxOffset = document.getLineEndOffset(lineNumber);
    if (newOffset > maxOffset) {
      if (lineNumber + 1 >= document.getLineCount()) return offset;
      maxOffset = document.getLineEndOffset(lineNumber + 1);
    }
    boolean camel = myEditor.getSettings().isCamelWords();
    for (; newOffset < maxOffset; newOffset++) {
      if (EditorActionUtil.isWordEnd(text, newOffset, camel)) break;
    }

    return newOffset;
  }

  @Override
  public void copySelectionToClipboard() {
    validateContext(true);
    String s = myEditor.getSelectionModel().getSelectedText();
    if (s == null) return;

    s = StringUtil.convertLineSeparators(s);
    StringSelection contents = new StringSelection(s);
    CopyPasteManager.getInstance().setContents(contents);
  }

  @Override
  public TextAttributes getTextAttributes() {
    if (myTextAttributes == null) {
      TextAttributes textAttributes = new TextAttributes();
      EditorColorsScheme scheme = myEditor.getColorsScheme();
      textAttributes.setForegroundColor(scheme.getColor(EditorColors.SELECTION_FOREGROUND_COLOR));
      textAttributes.setBackgroundColor(scheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR));
      myTextAttributes = textAttributes;
    }

    return myTextAttributes;
  }

  public void reinitSettings() {
    myTextAttributes = null;
  }
}
