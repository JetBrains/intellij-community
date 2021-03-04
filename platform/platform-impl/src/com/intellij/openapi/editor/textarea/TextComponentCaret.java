// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.textarea;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

class TextComponentCaret extends UserDataHolderBase implements Caret {
  private final TextComponentEditorImpl myEditor;

  TextComponentCaret(TextComponentEditorImpl editor) {
    myEditor = editor;
  }

  @NotNull
  @Override
  public Editor getEditor() {
    return myEditor;
  }

  @NotNull
  @Override
  public CaretModel getCaretModel() {
    return myEditor.getCaretModel();
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void moveCaretRelatively(int columnShift, int lineShift, boolean withSelection, boolean scrollToCaret) {
    getCaretModel().moveCaretRelatively(columnShift, lineShift, withSelection, false, scrollToCaret);
  }

  @Override
  public void moveToLogicalPosition(@NotNull LogicalPosition pos) {
    moveToOffset(myEditor.logicalPositionToOffset(pos), false);
  }

  @Override
  public void moveToVisualPosition(@NotNull VisualPosition pos) {
    moveToLogicalPosition(myEditor.visualToLogicalPosition(pos));
  }

  @Override
  public void moveToOffset(int offset) {
    moveToOffset(offset, false);
  }

  @Override
  public void moveToOffset(int offset, boolean locateBeforeSoftWrap) {
    JTextComponent textComponent = getTextComponent();
    int targetOffset = Math.min(offset, textComponent.getText().length());
    int currentPosition = textComponent.getCaretPosition();
    // We try to preserve selection, to match EditorImpl behaviour.
    // It's only possible though, if target offset is located at either end of existing selection.
    if (targetOffset != currentPosition) {
      if (targetOffset == textComponent.getCaret().getMark()) {
        textComponent.setCaretPosition(currentPosition);
        textComponent.moveCaretPosition(targetOffset);
      }
      else {
        textComponent.setCaretPosition(targetOffset);
      }
    }
  }

  @Override
  public boolean isUpToDate() {
    return true;
  }

  @NotNull
  @Override
  public LogicalPosition getLogicalPosition() {
    JTextComponent textComponent = getTextComponent();
    int caretPos = textComponent.getCaretPosition();
    int line;
    int lineStart;
    if (textComponent instanceof JTextArea) {
      final JTextArea textArea = (JTextArea)textComponent;
      try {
        line = textArea.getLineOfOffset(caretPos);
        lineStart = textArea.getLineStartOffset(line);
      }
      catch (BadLocationException e) {
        throw new RuntimeException(e);
      }
    }
    else {
      line = 0;
      lineStart = 0;
    }
    return new LogicalPosition(line, caretPos - lineStart);
  }

  @NotNull
  @Override
  public VisualPosition getVisualPosition() {
    LogicalPosition pos = getLogicalPosition();
    return new VisualPosition(pos.line, pos.column);
  }

  @Override
  public int getOffset() {
    return getTextComponent().getCaretPosition();
  }

  @Override
  public int getVisualLineStart() {
    return 0;
  }

  @Override
  public int getVisualLineEnd() {
    return 0;
  }

  @Override
  public int getSelectionStart() {
    return getTextComponent().getSelectionStart();
  }

  @NotNull
  @Override
  public VisualPosition getSelectionStartPosition() {
    return myEditor.offsetToVisualPosition(getSelectionStart());
  }

  @Override
  public int getSelectionEnd() {
    return getTextComponent().getSelectionEnd();
  }

  @NotNull
  @Override
  public VisualPosition getSelectionEndPosition() {
    return myEditor.offsetToVisualPosition(getSelectionEnd());
  }

  @Nullable
  @Override
  public String getSelectedText() {
    return getTextComponent().getSelectedText();
  }

  @Override
  public int getLeadSelectionOffset() {
    JTextComponent textComponent = getTextComponent();
    final int caretPosition = textComponent.getCaretPosition();
    final int start = textComponent.getSelectionStart();
    final int end = textComponent.getSelectionEnd();
    return caretPosition == start ? end : start;
  }

  @NotNull
  @Override
  public VisualPosition getLeadSelectionPosition() {
    return myEditor.offsetToVisualPosition(getLeadSelectionOffset());
  }

  @Override
  public boolean hasSelection() {
    return getSelectionStart() != getSelectionEnd();
  }

  @Override
  public void setSelection(int startOffset, int endOffset) {
    JTextComponent textComponent = getTextComponent();
    if (textComponent.getCaretPosition() == startOffset) {   // avoid moving caret (required for correct Ctrl-W operation)
      textComponent.setCaretPosition(endOffset);
      textComponent.moveCaretPosition(startOffset);
    }
    else {
      textComponent.setCaretPosition(startOffset);
      textComponent.moveCaretPosition(endOffset);
    }
  }

  @Override
  public void setSelection(int startOffset, int endOffset, boolean updateSystemSelection) {
    // updating system selection is not supported currently for TextComponentEditor
    setSelection(startOffset, endOffset);
  }

  @Override
  public void setSelection(int startOffset, @Nullable VisualPosition endPosition, int endOffset) {
    setSelection(startOffset, endOffset);
  }

  @Override
  public void setSelection(@Nullable VisualPosition startPosition, int startOffset, @Nullable VisualPosition endPosition, int endOffset) {
    setSelection(startOffset, endOffset);
  }

  @Override
  public void setSelection(@Nullable VisualPosition startPosition, int startOffset, @Nullable VisualPosition endPosition, int endOffset,
                           boolean updateSystemSelection) {
    // updating system selection is not supported currently for TextComponentEditor
    setSelection(startOffset, endOffset);
  }

  @Override
  public void removeSelection() {
    JTextComponent textComponent = getTextComponent();
    final int position = textComponent.getCaretPosition();
    textComponent.select(position, position);
  }

  @Override
  public void selectLineAtCaret() {
    EditorActionUtil.selectEntireLines(this, true);
  }

  @Override
  public void selectWordAtCaret(boolean honorCamelWordsSettings) {
    removeSelection();

    EditorActionHandler handler = EditorActionManager.getInstance().getActionHandler(
      IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
    handler.execute(myEditor, null, DataManager.getInstance().getDataContext(myEditor.getComponent()));
  }

  @Nullable
  @Override
  public Caret clone(boolean above) {
    return null;
  }

  @Override
  public void dispose() {
  }

  @Override
  public boolean isAtRtlLocation() {
    return false;
  }

  @Override
  public boolean isAtBidiRunBoundary() {
    return false;
  }

  @NotNull
  @Override
  public CaretVisualAttributes getVisualAttributes() {
    return CaretVisualAttributes.DEFAULT;
  }

  @Override
  public void setVisualAttributes(@NotNull CaretVisualAttributes attributes) {
  }

  private JTextComponent getTextComponent() {
    return myEditor.getContentComponent();
  }
}
