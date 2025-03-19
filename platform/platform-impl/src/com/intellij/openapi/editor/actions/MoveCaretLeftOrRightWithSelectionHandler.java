// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.keymap.impl.ModifierKeyDoubleClickHandler;
import org.jetbrains.annotations.NotNull;

final class MoveCaretLeftOrRightWithSelectionHandler extends EditorActionHandler.ForEachCaret {
  private final boolean myMoveRight;

  MoveCaretLeftOrRightWithSelectionHandler(boolean moveRight) {
    myMoveRight = moveRight;
  }

  @Override
  protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    return !ModifierKeyDoubleClickHandler.getInstance().isRunningAction() ||
           EditorSettingsExternalizable.getInstance().addCaretsOnDoubleCtrl();
  }

  @Override
  protected void doExecute(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    VisualPosition currentPosition = caret.getVisualPosition();
    if (caret.isAtBidiRunBoundary() && (myMoveRight ^ currentPosition.leansRight)) {
      int selectionStartToUse = caret.getLeadSelectionOffset();
      VisualPosition selectionStartPositionToUse = caret.getLeadSelectionPosition();
      caret.moveToVisualPosition(currentPosition.leanRight(!currentPosition.leansRight));
      caret.setSelection(selectionStartPositionToUse, selectionStartToUse, caret.getVisualPosition(), caret.getOffset());
    }
    else {
      editor.getCaretModel().moveCaretRelatively(myMoveRight ? 1 : -1, 0, true, editor.isColumnMode(),
                                                 caret == editor.getCaretModel().getPrimaryCaret());
    }
  }
}
