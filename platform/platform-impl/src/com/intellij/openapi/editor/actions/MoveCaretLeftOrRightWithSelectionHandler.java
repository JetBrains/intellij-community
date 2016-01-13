/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.keymap.impl.ModifierKeyDoubleClickHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class MoveCaretLeftOrRightWithSelectionHandler extends EditorActionHandler {
  private final boolean myMoveRight;

  MoveCaretLeftOrRightWithSelectionHandler(boolean moveRight) {
    super(true);
    myMoveRight = moveRight;
  }

  @Override
  protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    return !ModifierKeyDoubleClickHandler.getInstance().isRunningAction() ||
           EditorSettingsExternalizable.getInstance().addCaretsOnDoubleCtrl();
  }

  @Override
  protected void doExecute(Editor editor, @Nullable Caret caret, DataContext dataContext) {
    assert caret != null;
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
