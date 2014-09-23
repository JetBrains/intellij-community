/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class SelectionModelImpl implements SelectionModel, PrioritizedDocumentListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.SelectionModelImpl");

  private final List<SelectionListener> mySelectionListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final EditorImpl myEditor;

  private TextAttributes myTextAttributes;

  private LogicalPosition myBlockStart;
  private LogicalPosition myBlockEnd;
  private int[] myBlockSelectionStarts;
  private int[] myBlockSelectionEnds;

  private DocumentEvent myIsInUpdate;

  public SelectionModelImpl(EditorImpl editor) {
    myEditor = editor;
  }

  @Override
  public void beforeDocumentChange(DocumentEvent event) {
    myIsInUpdate = event;
    for (Caret caret : myEditor.getCaretModel().getAllCarets()) {
      ((CaretImpl)caret).beforeDocumentChange();
    }
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    if (myIsInUpdate == event) {
      myIsInUpdate = null;
      myEditor.getCaretModel().doWithCaretMerging(new Runnable() {
        public void run() {
          for (Caret caret : myEditor.getCaretModel().getAllCarets()) {
            ((CaretImpl)caret).documentChanged();
          }
        }
      });
    }
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.SELECTION_MODEL;
  }

  /**
   * @see CaretImpl#setUnknownDirection(boolean)
   */
  public boolean isUnknownDirection() {
    return myEditor.getCaretModel().getCurrentCaret().isUnknownDirection();
  }

  /**
   * @see CaretImpl#setUnknownDirection(boolean)
   */
  public void setUnknownDirection(boolean unknownDirection) {
    myEditor.getCaretModel().getCurrentCaret().setUnknownDirection(unknownDirection);
  }

  @Override
  public int getSelectionStart() {
    return myEditor.getCaretModel().getCurrentCaret().getSelectionStart();
  }

  @NotNull
  @Override
  public VisualPosition getSelectionStartPosition() {
    return myEditor.getCaretModel().getCurrentCaret().getSelectionStartPosition();
  }

  @Override
  public int getSelectionEnd() {
    return myEditor.getCaretModel().getCurrentCaret().getSelectionEnd();
  }

  @NotNull
  @Override
  public VisualPosition getSelectionEndPosition() {
    return myEditor.getCaretModel().getCurrentCaret().getSelectionEndPosition();
  }

  @Override
  public boolean hasSelection() {
    return hasSelection(false);
  }

  @Override
  public boolean hasSelection(boolean anyCaret) {
    if (!anyCaret) {
      return myEditor.getCaretModel().getCurrentCaret().hasSelection();
    }
    for (Caret caret : myEditor.getCaretModel().getAllCarets()) {
      if (caret.hasSelection()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void setSelection(int startOffset, int endOffset) {
    myEditor.getCaretModel().getCurrentCaret().setSelection(startOffset, endOffset);
  }

  @Override
  public void setSelection(int startOffset, @Nullable VisualPosition endPosition, int endOffset) {
    myEditor.getCaretModel().getCurrentCaret().setSelection(startOffset, endPosition, endOffset);
  }

  @Override
  public void setSelection(@Nullable VisualPosition startPosition, int startOffset, @Nullable VisualPosition endPosition, int endOffset) {
    myEditor.getCaretModel().getCurrentCaret().setSelection(startPosition, startOffset, endPosition, endOffset);
  }

  void fireSelectionChanged(int oldSelectionStart, int oldSelectionEnd, int startOffset, int endOffset) {
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
  public void removeSelection() {
    removeSelection(false);
  }

  @Override
  public void removeSelection(boolean allCarets) {
    if (!allCarets) {
      myEditor.getCaretModel().getCurrentCaret().removeSelection();
    }
    else {
      for (Caret caret : myEditor.getCaretModel().getAllCarets()) {
        caret.removeSelection();
      }
    }
  }

  @Override
  public void setBlockSelection(@NotNull LogicalPosition blockStart, @NotNull LogicalPosition blockEnd) {
    if (myEditor.getCaretModel().supportsMultipleCarets()) {
      int startLine = Math.max(Math.min(blockStart.line, myEditor.getDocument().getLineCount() - 1), 0);
      int endLine = Math.max(Math.min(blockEnd.line, myEditor.getDocument().getLineCount() - 1), 0);
      int step = endLine < startLine ? -1 : 1;
      int count = 1 + Math.abs(endLine - startLine);
      List<CaretState> caretStates = new LinkedList<CaretState>();
      boolean hasSelection = false;
      for (int line = startLine, i = 0; i < count; i++, line += step) {
        int startColumn = blockStart.column;
        int endColumn = blockEnd.column;
        int lineEndOffset = myEditor.getDocument().getLineEndOffset(line);
        LogicalPosition lineEndPosition = myEditor.offsetToLogicalPosition(lineEndOffset);
        int lineWidth = lineEndPosition.column;
        if (startColumn > lineWidth && endColumn > lineWidth && !myEditor.isColumnMode()) {
          LogicalPosition caretPos = new LogicalPosition(line, Math.min(startColumn, endColumn));
          caretStates.add(new CaretState(caretPos,
                                         lineEndPosition,
                                         lineEndPosition));
        }
        else {
          LogicalPosition startPos = new LogicalPosition(line, myEditor.isColumnMode() ? startColumn : Math.min(startColumn, lineWidth));
          LogicalPosition endPos = new LogicalPosition(line, myEditor.isColumnMode() ? endColumn : Math.min(endColumn, lineWidth));
          int startOffset = myEditor.logicalPositionToOffset(startPos);
          int endOffset = myEditor.logicalPositionToOffset(endPos);
          caretStates.add(new CaretState(endPos, startPos, endPos));
          hasSelection |= startOffset != endOffset;
        }
      }
      if (hasSelection && !myEditor.isColumnMode()) { // filtering out lines without selection
        Iterator<CaretState> caretStateIterator = caretStates.iterator();
        while(caretStateIterator.hasNext()) {
          CaretState state = caretStateIterator.next();
          //noinspection ConstantConditions
          if (state.getSelectionStart().equals(state.getSelectionEnd())) {
            caretStateIterator.remove();
          }
        }
      }
      myEditor.getCaretModel().setCaretsAndSelections(caretStates);
    }
    else {
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
  }

  @Override
  public void removeBlockSelection() {
    if (!myEditor.getCaretModel().supportsMultipleCarets()) {
      myEditor.getCaretModel().getCurrentCaret().setUnknownDirection(false);
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
    if (myEditor.getCaretModel().supportsMultipleCarets()) {
      Collection<Caret> carets = myEditor.getCaretModel().getAllCarets();
      int[] result = new int[carets.size()];
      int i = 0;
      for (Caret caret : carets) {
        result[i++] = caret.getSelectionStart();
      }
      return result;
    } else {
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
  }

  @Override
  @NotNull
  public int[] getBlockSelectionEnds() {
    if (myEditor.getCaretModel().supportsMultipleCarets()) {
      Collection<Caret> carets = myEditor.getCaretModel().getAllCarets();
      int[] result = new int[carets.size()];
      int i = 0;
      for (Caret caret : carets) {
        result[i++] = caret.getSelectionEnd();
      }
      return result;
    } else {
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
  }

  @Override
  public void addSelectionListener(SelectionListener listener) {
    mySelectionListeners.add(listener);
  }

  public void addSelectionListener(final SelectionListener listener, Disposable parent) {
    mySelectionListeners.add(listener);
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        mySelectionListeners.remove(listener);
      }
    });
  }

  @Override
  public void removeSelectionListener(SelectionListener listener) {
    boolean success = mySelectionListeners.remove(listener);
    LOG.assertTrue(success);
  }

  @Override
  public String getSelectedText() {
    return getSelectedText(false);
  }

  @Override
  public String getSelectedText(boolean allCarets) {
    validateContext(false);

    if (hasBlockSelection()) {
      CharSequence text = myEditor.getDocument().getCharsSequence();
      int[] starts = getBlockSelectionStarts();
      int[] ends = getBlockSelectionEnds();
      int width = myEditor.getCaretModel().supportsMultipleCarets() ? 0 : Math.abs(myBlockEnd.column - myBlockStart.column);
      final StringBuilder buf = new StringBuilder();
      for (int i = 0; i < starts.length; i++) {
        if (i > 0) buf.append('\n');
        final int len = ends[i] - starts[i];
        appendCharSequence(buf, text, starts[i], len);
        for (int j = len; j < width; j++) buf.append(' ');
      }
      return buf.toString();
    }
    else if (myEditor.getCaretModel().supportsMultipleCarets() && allCarets) {
      final StringBuilder buf = new StringBuilder();
      String separator = "";
      for (Caret caret : myEditor.getCaretModel().getAllCarets()) {
        buf.append(separator);
        String caretSelectedText = caret.getSelectedText();
        if (caretSelectedText != null) {
          buf.append(caretSelectedText);
        }
        separator = "\n";
      }
      return buf.toString();
    }
    else {
      return myEditor.getCaretModel().getCurrentCaret().getSelectedText();
    }
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
  public int getLeadSelectionOffset() {
    return myEditor.getCaretModel().getCurrentCaret().getLeadSelectionOffset();
  }

  @NotNull
  @Override
  public VisualPosition getLeadSelectionPosition() {
    return myEditor.getCaretModel().getCurrentCaret().getLeadSelectionPosition();
  }

  @Override
  public void selectLineAtCaret() {
    myEditor.getCaretModel().getCurrentCaret().selectLineAtCaret();
  }

  @Override
  public void selectWordAtCaret(boolean honorCamelWordsSettings) {
    myEditor.getCaretModel().getCurrentCaret().selectWordAtCaret(honorCamelWordsSettings);
  }

  @Override
  public void copySelectionToClipboard() {
    EditorCopyPasteHelper.getInstance().copySelectionToClipboard(myEditor);
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

  private void validateContext(boolean isWrite) {
    if (!myEditor.getComponent().isShowing()) return;
    if (isWrite) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }
    else {
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
  }
}
