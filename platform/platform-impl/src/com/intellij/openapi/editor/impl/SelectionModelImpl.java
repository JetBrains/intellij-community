// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.editor.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class SelectionModelImpl implements SelectionModel {
  private static final Logger LOG = Logger.getInstance(SelectionModelImpl.class);

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
  public @NotNull Editor getEditor() {
    return myEditor;
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
  public void setBlockSelection(@NotNull LogicalPosition blockStart, @NotNull LogicalPosition blockEnd) {
    List<CaretState> caretStates = EditorModificationUtil.calcBlockSelectionState(myEditor, blockStart, blockEnd);
    myEditor.getCaretModel().setCaretsAndSelections(caretStates);
  }

  @Override
  public int @NotNull [] getBlockSelectionStarts() {
    Collection<Caret> carets = myEditor.getCaretModel().getAllCarets();
    int[] result = new int[carets.size()];
    int i = 0;
    for (Caret caret : carets) {
      result[i++] = caret.getSelectionStart();
    }
    return result;
  }

  @Override
  public int @NotNull [] getBlockSelectionEnds() {
    Collection<Caret> carets = myEditor.getCaretModel().getAllCarets();
    int[] result = new int[carets.size()];
    int i = 0;
    for (Caret caret : carets) {
      result[i++] = caret.getSelectionEnd();
    }
    return result;
  }

  @Override
  public void addSelectionListener(@NotNull SelectionListener listener) {
    mySelectionListeners.add(listener);
  }

  @Override
  public void removeSelectionListener(@NotNull SelectionListener listener) {
    boolean success = mySelectionListeners.remove(listener);
    LOG.assertTrue(success);
  }

  /**
   * @deprecated Use {@link EditorActionUtil#selectEntireLines} instead.
   */
  @Deprecated
  public static void doSelectLineAtCaret(@NotNull Caret caret) {
    EditorActionUtil.selectEntireLines(caret, true);
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
