/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class SelectionModelImpl implements SelectionModel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.SelectionModelImpl");

  private final List<SelectionListener> mySelectionListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final EditorImpl myEditor;

  private TextAttributes myTextAttributes;

  public SelectionModelImpl(EditorImpl editor) {
    myEditor = editor;
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

  void fireSelectionChanged(SelectionEvent event) {
    TextRange[] oldRanges = event.getOldRanges();
    TextRange[] newRanges = event.getNewRanges();
    int count = Math.min(oldRanges.length, newRanges.length);
    for (int i = 0; i < count; i++) {
      TextRange oldRange = oldRanges[i];
      TextRange newRange = newRanges[i];
      int oldSelectionStart = oldRange.getStartOffset();
      int startOffset = newRange.getStartOffset();
      int oldSelectionEnd = oldRange.getEndOffset();
      int endOffset = newRange.getEndOffset();
      myEditor.repaint(Math.min(oldSelectionStart, startOffset), Math.max(oldSelectionStart, startOffset), false);
      myEditor.repaint(Math.min(oldSelectionEnd, endOffset), Math.max(oldSelectionEnd, endOffset), false);
    }
    TextRange[] remaining = oldRanges.length < newRanges.length ? newRanges : oldRanges;
    for (int i = count; i < remaining.length; i++) {
      TextRange range = remaining[i];
      myEditor.repaint(range.getStartOffset(), range.getEndOffset(), false);
    }

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
    List<CaretState> caretStates = EditorModificationUtil.calcBlockSelectionState(myEditor, blockStart, blockEnd);
    myEditor.getCaretModel().setCaretsAndSelections(caretStates);
  }

  @Override
  @NotNull
  public int[] getBlockSelectionStarts() {
    Collection<Caret> carets = myEditor.getCaretModel().getAllCarets();
    int[] result = new int[carets.size()];
    int i = 0;
    for (Caret caret : carets) {
      result[i++] = caret.getSelectionStart();
    }
    return result;
  }

  @Override
  @NotNull
  public int[] getBlockSelectionEnds() {
    Collection<Caret> carets = myEditor.getCaretModel().getAllCarets();
    int[] result = new int[carets.size()];
    int i = 0;
    for (Caret caret : carets) {
      result[i++] = caret.getSelectionEnd();
    }
    return result;
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
    ApplicationManager.getApplication().assertReadAccessAllowed();

    if (myEditor.getCaretModel().supportsMultipleCarets() && allCarets) {
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

  public static void doSelectLineAtCaret(Caret caret) {
    Editor editor = caret.getEditor();
    int lineNumber = caret.getLogicalPosition().line;
    Document document = editor.getDocument();
    if (lineNumber >= document.getLineCount()) {
      return;
    }

    Pair<LogicalPosition, LogicalPosition> lines = EditorUtil.calcCaretLineRange(caret);
    LogicalPosition lineStart = lines.first;
    LogicalPosition nextLineStart = lines.second;

    int start = editor.logicalPositionToOffset(lineStart);
    int end = editor.logicalPositionToOffset(nextLineStart);

    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    caret.removeSelection();
    caret.setSelection(start, end);
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
}
