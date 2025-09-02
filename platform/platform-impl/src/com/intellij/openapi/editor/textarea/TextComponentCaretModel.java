// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.textarea;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;


final class TextComponentCaretModel implements CaretModel {
  private final TextComponentEditor myEditor;
  private final Caret myCaret;
  private final EventDispatcher<CaretActionListener> myCaretActionListeners = EventDispatcher.create(CaretActionListener.class);

  TextComponentCaretModel(@NotNull TextComponentEditorImpl editor) {
    myEditor = editor;
    myCaret = new TextComponentCaret(editor);
  }

  @Override
  public void moveCaretRelatively(final int columnShift,
                                  final int lineShift,
                                  final boolean withSelection, final boolean blockSelection, final boolean scrollToCaret) {
    if (lineShift == 0 && !withSelection && !blockSelection) {
      moveToOffset(getOffset() + columnShift);
      if (scrollToCaret) {
        myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
      return;
    }
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void addCaretListener(final @NotNull CaretListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void removeCaretListener(final @NotNull CaretListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public TextAttributes getTextAttributes() {
    return null;
  }

  @Override
  public boolean supportsMultipleCarets() {
    return false;
  }

  @Override
  public int getMaxCaretCount() {
    return 1;
  }

  @Override
  public @NotNull Caret getCurrentCaret() {
    return myCaret;
  }

  @Override
  public @NotNull Caret getPrimaryCaret() {
    return myCaret;
  }

  @Override
  public int getCaretCount() {
    return 1;
  }

  @Override
  public @NotNull List<Caret> getAllCarets() {
    return Collections.singletonList(myCaret);
  }

  @Override
  public @Nullable Caret getCaretAt(@NotNull VisualPosition pos) {
    return myCaret.getVisualPosition().equals(pos) ? myCaret : null;
  }

  @Override
  public @Nullable Caret addCaret(@NotNull VisualPosition pos, boolean makePrimary) {
    return null;
  }

  @Override
  public @Nullable Caret addCaret(@NotNull LogicalPosition pos, boolean makePrimary) {
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
  public void setCaretsAndSelections(@NotNull List<? extends CaretState> caretStates) {
    if (caretStates.isEmpty()) throw new IllegalArgumentException("Empty list");
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
  public void setCaretsAndSelections(@NotNull List<? extends CaretState> caretStates, boolean updateSystemSelection) {
    setCaretsAndSelections(caretStates);
  }

  @Override
  public @NotNull List<CaretState> getCaretsAndSelections() {
    return Collections.singletonList(new CaretState(getLogicalPosition(), 
                                                    myEditor.offsetToLogicalPosition(myEditor.getSelectionModel().getSelectionStart()), 
                                                    myEditor.offsetToLogicalPosition(myEditor.getSelectionModel().getSelectionEnd())));
  }

  @Override
  public void runForEachCaret(@NotNull CaretAction action) {
    myCaretActionListeners.getMulticaster().beforeAllCaretsAction();
    try {
      action.perform(myCaret);
    } finally {
      myCaretActionListeners.getMulticaster().afterAllCaretsAction();
    }
  }

  @Override
  public void runForEachCaret(@NotNull CaretAction action, boolean reverseOrder) {
    runForEachCaret(action);
  }

  @Override
  public void addCaretActionListener(@NotNull CaretActionListener listener, @NotNull Disposable disposable) {
    myCaretActionListeners.addListener(listener, disposable);
  }

  @Override
  public void runBatchCaretOperation(@NotNull Runnable runnable) {
    runnable.run();
  }
}
