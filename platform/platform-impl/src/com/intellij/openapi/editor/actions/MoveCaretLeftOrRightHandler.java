// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import org.jetbrains.annotations.NotNull;

final class MoveCaretLeftOrRightHandler extends EditorActionHandler.ForEachCaret {
  enum Direction {LEFT, RIGHT}

  private final Direction myDirection;

  MoveCaretLeftOrRightHandler(Direction direction) {
    myDirection = direction;
  }

  @Override
  public void doExecute(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    final CaretModel caretModel = editor.getCaretModel();
    ScrollingModel scrollingModel = editor.getScrollingModel();

    if (selectionModel.hasSelection() && (!(editor instanceof EditorEx) || !((EditorEx)editor).isStickySelection())) {
      int start = selectionModel.getSelectionStart();
      int end = selectionModel.getSelectionEnd();
      int caretOffset = caretModel.getOffset();

      if (start <= caretOffset && end >= caretOffset) { // See IDEADEV-36957

        VisualPosition targetPosition = myDirection == Direction.RIGHT ? caret.getSelectionEndPosition()
                                                                       : caret.getSelectionStartPosition();

        Runnable runnable = () -> {
          selectionModel.removeSelection();
          caretModel.moveToVisualPosition(targetPosition);
          if (caret == editor.getCaretModel().getPrimaryCaret()) {
            scrollingModel.scrollToCaret(ScrollType.RELATIVE);
          }
        };
        EditorUtil.runWithAnimationDisabled(editor, runnable);
        return;
      }
    }

    Runnable runnable = () -> {
      VisualPosition currentPosition = caret.getVisualPosition();
      if (caret.isAtBidiRunBoundary() && (myDirection == Direction.RIGHT ^ currentPosition.leansRight)) {
        caret.moveToVisualPosition(currentPosition.leanRight(!currentPosition.leansRight));
      }
      else {
        final boolean scrollToCaret = (!(editor instanceof EditorImpl) || ((EditorImpl)editor).isScrollToCaret())
                                      && caret == editor.getCaretModel().getPrimaryCaret();
        caretModel.moveCaretRelatively(myDirection == Direction.RIGHT ? 1 : -1, 0, false, false, scrollToCaret);
      }
    };
    EditorUtil.runWithAnimationDisabled(editor, runnable);
  }
}
