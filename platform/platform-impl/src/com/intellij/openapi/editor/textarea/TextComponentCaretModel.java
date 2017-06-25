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
package com.intellij.openapi.editor.textarea;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class TextComponentCaretModel implements CaretModel {
  private final JTextComponent myTextComponent;
  private final TextComponentEditor myEditor;
  private final Caret myCaret;

  public TextComponentCaretModel(@NotNull JTextComponent textComponent, @NotNull TextComponentEditor editor) {
    myTextComponent = textComponent;
    myEditor = editor;
    myCaret = new TextComponentCaret(editor);
  }

  @Override
  public void moveCaretRelatively(final int columnShift,
                                  final int lineShift,
                                  final boolean withSelection, final boolean blockSelection, final boolean scrollToCaret) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void moveToLogicalPosition(@NotNull final LogicalPosition pos) {
    moveToOffset(myEditor.logicalPositionToOffset(pos), false);
  }

  @Override
  public void moveToVisualPosition(@NotNull final VisualPosition pos) {
    moveToLogicalPosition(myEditor.visualToLogicalPosition(pos));
  }

  @Override
  public void moveToOffset(int offset) {
    moveToOffset(offset, false);
  }

  @Override
  public void moveToOffset(final int offset, boolean locateBeforeSoftWrap) {
    int targetOffset = Math.min(offset, myTextComponent.getText().length());
    int currentPosition = myTextComponent.getCaretPosition();
    // We try to preserve selection, to match EditorImpl behaviour.
    // It's only possible though, if target offset is located at either end of existing selection.
    if (targetOffset != currentPosition) {
      if (targetOffset == myTextComponent.getCaret().getMark()) {
        myTextComponent.setCaretPosition(currentPosition);
        myTextComponent.moveCaretPosition(targetOffset);
      }
      else {
        myTextComponent.setCaretPosition(targetOffset);
      }
    }
  }

  @Override
  public boolean isUpToDate() {
    return true;
  }

  @Override
  @NotNull
  public LogicalPosition getLogicalPosition() {
    int caretPos = myTextComponent.getCaretPosition();
    int line;
    int lineStart;
    if (myTextComponent instanceof JTextArea) {
      final JTextArea textArea = (JTextArea)myTextComponent;
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

  @Override
  @NotNull
  public VisualPosition getVisualPosition() {
    LogicalPosition pos = getLogicalPosition();
    return new VisualPosition(pos.line, pos.column);
  }

  @Override
  public int getOffset() {
    return myTextComponent.getCaretPosition();
  }

  @Override
  public void addCaretListener(@NotNull final CaretListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void removeCaretListener(@NotNull final CaretListener listener) {
    throw new UnsupportedOperationException("Not implemented");
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
  public TextAttributes getTextAttributes() {
    return null;
  }

  @Override
  public boolean supportsMultipleCarets() {
    return false;
  }

  @NotNull
  @Override
  public Caret getCurrentCaret() {
    return myCaret;
  }

  @NotNull
  @Override
  public Caret getPrimaryCaret() {
    return myCaret;
  }

  @Override
  public int getCaretCount() {
    return 1;
  }

  @NotNull
  @Override
  public List<Caret> getAllCarets() {
    return Collections.singletonList(myCaret);
  }

  @Nullable
  @Override
  public Caret getCaretAt(@NotNull VisualPosition pos) {
    return myCaret.getVisualPosition().equals(pos) ? myCaret : null;
  }

  @Nullable
  @Override
  public Caret addCaret(@NotNull VisualPosition pos) {
    return null;
  }

  @Nullable
  @Override
  public Caret addCaret(@NotNull VisualPosition pos, boolean makePrimary) {
    return null;
  }

  @Override
  public boolean removeCaret(@NotNull Caret caret) {
    return false;
  }

  @Override
  public void removeSecondaryCarets() {
  }

  @Override
  public void setCaretsAndSelections(@NotNull List<CaretState> caretStates) {
    if (caretStates.size() != 1) throw new IllegalArgumentException("Exactly one CaretState object must be passed");
    CaretState state = caretStates.get(0);
    if (state != null) {
      if (state.getCaretPosition() != null) moveToLogicalPosition(state.getCaretPosition());
      if (state.getSelectionStart() != null && state.getSelectionEnd() != null) {
        myEditor.getSelectionModel().setSelection(myEditor.logicalPositionToOffset(state.getSelectionStart()), 
                                                  myEditor.logicalPositionToOffset(state.getSelectionEnd()));
      }
    }
  }

  @Override
  public void setCaretsAndSelections(@NotNull List<CaretState> caretStates, boolean updateSystemSelection) {
    setCaretsAndSelections(caretStates);
  }

  @NotNull
  @Override
  public List<CaretState> getCaretsAndSelections() {
    return Collections.singletonList(new CaretState(getLogicalPosition(), 
                                                    myEditor.offsetToLogicalPosition(myEditor.getSelectionModel().getSelectionStart()), 
                                                    myEditor.offsetToLogicalPosition(myEditor.getSelectionModel().getSelectionEnd())));
  }

  @Override
  public void runForEachCaret(@NotNull CaretAction action) {
    action.perform(myCaret);
  }

  @Override
  public void runForEachCaret(@NotNull CaretAction action, boolean reverseOrder) {
    action.perform(myCaret);
  }

  @Override
  public void runBatchCaretOperation(@NotNull Runnable runnable) {
    runnable.run();
  }
}
