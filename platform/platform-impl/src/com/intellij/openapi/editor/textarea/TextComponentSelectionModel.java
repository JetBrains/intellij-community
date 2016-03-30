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
package com.intellij.openapi.editor.textarea;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.impl.SelectionModelImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.text.JTextComponent;

/**
 * @author yole
 */
public class TextComponentSelectionModel implements SelectionModel {
  private final JTextComponent myTextComponent;
  private final TextComponentEditor myEditor;

  public TextComponentSelectionModel(@NotNull JTextComponent textComponent, @NotNull TextComponentEditor textComponentEditor) {
    myTextComponent = textComponent;
    myEditor = textComponentEditor;
  }

  @Override
  public int getSelectionStart() {
    return myTextComponent.getSelectionStart();
  }

  @Nullable
  @Override
  public VisualPosition getSelectionStartPosition() {
    return null;
  }

  @Override
  public int getSelectionEnd() {
    return myTextComponent.getSelectionEnd();
  }

  @Nullable
  @Override
  public VisualPosition getSelectionEndPosition() {
    return null;
  }

  @Override
  @Nullable
  public String getSelectedText() {
    return myTextComponent.getSelectedText();
  }

  @Nullable
  @Override
  public String getSelectedText(boolean allCarets) {
    return getSelectedText();
  }

  @Override
  public int getLeadSelectionOffset() {
    final int caretPosition = myTextComponent.getCaretPosition();
    final int start = myTextComponent.getSelectionStart();
    final int end = myTextComponent.getSelectionEnd();
    return caretPosition == start ? end : start;
  }

  @Nullable
  @Override
  public VisualPosition getLeadSelectionPosition() {
    return null;
  }

  @Override
  public boolean hasSelection() {
    return myTextComponent.getSelectionStart() != myTextComponent.getSelectionEnd();
  }

  @Override
  public boolean hasSelection(boolean anyCaret) {
    return hasSelection();
  }

  @Override
  public void setSelection(final int startOffset, final int endOffset) {
    if (myTextComponent.getCaretPosition() == startOffset) {   // avoid moving caret (required for correct Ctrl-W operation)
      myTextComponent.setCaretPosition(endOffset);
      myTextComponent.moveCaretPosition(startOffset);
    }
    else {
      myTextComponent.setCaretPosition(startOffset);
      myTextComponent.moveCaretPosition(endOffset);
    }
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
  public void removeSelection() {
    final int position = myTextComponent.getCaretPosition();
    myTextComponent.select(position, position);
  }

  @Override
  public void removeSelection(boolean allCarets) {
    removeSelection();
  }

  @Override
  public void addSelectionListener(final SelectionListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void removeSelectionListener(final SelectionListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void selectLineAtCaret() {
    SelectionModelImpl.doSelectLineAtCaret(myEditor);
  }

  @Override
  public void selectWordAtCaret(final boolean honorCamelWordsSettings) {
    removeSelection();

    EditorActionHandler handler = EditorActionManager.getInstance().getActionHandler(
      IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
    handler.execute(myEditor, DataManager.getInstance().getDataContext(myEditor.getComponent()));
  }

  @Override
  public void copySelectionToClipboard() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void setBlockSelection(@NotNull final LogicalPosition blockStart, @NotNull final LogicalPosition blockEnd) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  @NotNull
  public int[] getBlockSelectionStarts() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  @NotNull
  public int[] getBlockSelectionEnds() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public TextAttributes getTextAttributes() {
    return null;
  }
}
