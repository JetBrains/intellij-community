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

import com.intellij.openapi.editor.*;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TextComponentCaret extends UserDataHolderBase implements Caret {
  private final Editor myEditor;
  private final CaretModel myCaretModel;
  private final SelectionModel mySelectionModel;

  public TextComponentCaret(Editor editor) {
    myEditor = editor;
    myCaretModel = editor.getCaretModel();
    mySelectionModel = editor.getSelectionModel();
  }

  @NotNull
  @Override
  public CaretModel getCaretModel() {
    return myCaretModel;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void moveCaretRelatively(int columnShift, int lineShift, boolean withSelection, boolean scrollToCaret) {
    myCaretModel.moveCaretRelatively(columnShift, lineShift, withSelection, false, scrollToCaret);
  }

  @Override
  public void moveToLogicalPosition(@NotNull LogicalPosition pos) {
    myCaretModel.moveToLogicalPosition(pos);
  }

  @Override
  public void moveToVisualPosition(@NotNull VisualPosition pos) {
    myCaretModel.moveToVisualPosition(pos);
  }

  @Override
  public void moveToOffset(int offset) {
    myCaretModel.moveToOffset(offset);
  }

  @Override
  public void moveToOffset(int offset, boolean locateBeforeSoftWrap) {
    myCaretModel.moveToOffset(offset, locateBeforeSoftWrap);
  }

  @Override
  public boolean isUpToDate() {
    return myCaretModel.isUpToDate();
  }

  @NotNull
  @Override
  public LogicalPosition getLogicalPosition() {
    return myCaretModel.getLogicalPosition();
  }

  @NotNull
  @Override
  public VisualPosition getVisualPosition() {
    return myCaretModel.getVisualPosition();
  }

  @Override
  public int getOffset() {
    return myCaretModel.getOffset();
  }

  @Override
  public int getVisualLineStart() {
    return myCaretModel.getVisualLineStart();
  }

  @Override
  public int getVisualLineEnd() {
    return myCaretModel.getVisualLineEnd();
  }

  @Override
  public int getSelectionStart() {
    return mySelectionModel.getSelectionStart();
  }

  @NotNull
  @Override
  public VisualPosition getSelectionStartPosition() {
    return myEditor.offsetToVisualPosition(mySelectionModel.getSelectionStart());
  }

  @Override
  public int getSelectionEnd() {
    return mySelectionModel.getSelectionEnd();
  }

  @NotNull
  @Override
  public VisualPosition getSelectionEndPosition() {
    return myEditor.offsetToVisualPosition(mySelectionModel.getSelectionEnd());
  }

  @Nullable
  @Override
  public String getSelectedText() {
    return mySelectionModel.getSelectedText();
  }

  @Override
  public int getLeadSelectionOffset() {
    return mySelectionModel.getLeadSelectionOffset();
  }

  @NotNull
  @Override
  public VisualPosition getLeadSelectionPosition() {
    return myEditor.offsetToVisualPosition(mySelectionModel.getLeadSelectionOffset());
  }

  @Override
  public boolean hasSelection() {
    return mySelectionModel.hasSelection();
  }

  @Override
  public void setSelection(int startOffset, int endOffset) {
    mySelectionModel.setSelection(startOffset, endOffset);
  }

  @Override
  public void setSelection(int startOffset, @Nullable VisualPosition endPosition, int endOffset) {
    mySelectionModel.setSelection(startOffset, endPosition, endOffset);
  }

  @Override
  public void setSelection(@Nullable VisualPosition startPosition, int startOffset, @Nullable VisualPosition endPosition, int endOffset) {
    mySelectionModel.setSelection(startPosition, startOffset, endPosition, endOffset);
  }

  @Override
  public void removeSelection() {
    mySelectionModel.removeSelection();
  }

  @Override
  public void selectLineAtCaret() {
    mySelectionModel.selectLineAtCaret();
  }

  @Override
  public void selectWordAtCaret(boolean honorCamelWordsSettings) {
    mySelectionModel.selectWordAtCaret(honorCamelWordsSettings);
  }

  @Nullable
  @Override
  public Caret clone(boolean above) {
    return null;
  }

  @Override
  public void dispose() {
  }
}
