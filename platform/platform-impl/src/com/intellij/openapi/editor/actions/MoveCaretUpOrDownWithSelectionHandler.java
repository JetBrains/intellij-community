// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class MoveCaretUpOrDownWithSelectionHandler extends EditorActionHandler {
  private final @NotNull MoveCaretUpOrDownHandler.Direction myDirection;

  MoveCaretUpOrDownWithSelectionHandler(@NotNull MoveCaretUpOrDownHandler.Direction direction) {
    myDirection = direction;
  }

  @Override
  public void doExecute(@NotNull final Editor editor, @Nullable Caret caret, DataContext dataContext) {
    int lineShift = myDirection == MoveCaretUpOrDownHandler.Direction.DOWN ? 1 : -1;
    if (!editor.getCaretModel().supportsMultipleCarets()) {
      editor.getCaretModel().moveCaretRelatively(0, lineShift, true, editor.isColumnMode(), true);
      return;
    }
    if (editor.isColumnMode()) {
      new CloneCaretActionHandler(myDirection == MoveCaretUpOrDownHandler.Direction.UP).execute(editor, caret, dataContext);
    }
    else {
      CaretAction caretAction = c -> c.moveCaretRelatively(0, lineShift, true,
                                                           c == editor.getCaretModel().getPrimaryCaret());
      if (caret == null) {
        editor.getCaretModel().runForEachCaret(caretAction);
      }
      else {
        caretAction.perform(caret);
      }
    }
  }
}
