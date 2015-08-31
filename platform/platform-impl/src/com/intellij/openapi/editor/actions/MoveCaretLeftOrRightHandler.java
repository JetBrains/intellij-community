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

/*
 * @author max
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.util.registry.Registry;

class MoveCaretLeftOrRightHandler extends EditorActionHandler {
  enum Direction {LEFT, RIGHT}

  private final Direction myDirection;

  MoveCaretLeftOrRightHandler(Direction direction) {
    super(true);
    myDirection = direction;
  }

  @Override
  public void doExecute(Editor editor, Caret caret, DataContext dataContext) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    final CaretModel caretModel = editor.getCaretModel();
    ScrollingModel scrollingModel = editor.getScrollingModel();

    if (selectionModel.hasSelection() && (!(editor instanceof EditorEx) || !((EditorEx)editor).isStickySelection())) {
      if (editor.getIndentsModel().getCaretIndentGuide() != null) {
        selectionModel.removeSelection();
      }
      else {
        int start = selectionModel.getSelectionStart();
        int end = selectionModel.getSelectionEnd();
        int caretOffset = caretModel.getOffset();

        if (start <= caretOffset && end >= caretOffset) { // See IDEADEV-36957

          VisualPosition targetPosition = null;
          if (Registry.is("editor.new.rendering")) {
            targetPosition = myDirection == Direction.RIGHT  ? caret.getSelectionEndPosition() : caret.getSelectionStartPosition();
          }
          else if (caretModel.supportsMultipleCarets() && editor.isColumnMode()) {
            targetPosition = myDirection == Direction.RIGHT ? 
                             selectionModel.getSelectionEndPosition() : selectionModel.getSelectionStartPosition();
          }

          selectionModel.removeSelection();
          if (targetPosition != null) {
            caretModel.moveToVisualPosition(targetPosition);
          }
          else {
            caretModel.moveToOffset(myDirection == Direction.RIGHT ^ caret.isAtRtlLocation() ? end : start);
          }
          if (caret == editor.getCaretModel().getPrimaryCaret()) {
            scrollingModel.scrollToCaret(ScrollType.RELATIVE);
          }
          return;
        }
      }
    }
    VisualPosition currentPosition = caret.getVisualPosition();
    if (caret.isAtBidiRunBoundary() && (myDirection == Direction.RIGHT ^ currentPosition.leansRight)) {
      caret.moveToVisualPosition(currentPosition.leanRight(!currentPosition.leansRight));
    }
    else {
      final boolean scrollToCaret = (!(editor instanceof EditorImpl) || ((EditorImpl)editor).isScrollToCaret())
                                    && caret == editor.getCaretModel().getPrimaryCaret();
      caretModel.moveCaretRelatively(myDirection == Direction.RIGHT ? 1 : -1, 0, false, false, scrollToCaret);
    }
  }
}
