// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.util.Couple;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public final class DuplicateLinesAction extends EditorAction {
  public DuplicateLinesAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler.ForEachCaret {
    @Override
    public void executeWriteAction(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      if (editor.getSelectionModel().hasSelection()) {
        int selStart = editor.getSelectionModel().getSelectionStart();
        int selEnd = editor.getSelectionModel().getSelectionEnd();
        if (selEnd > selStart && DocumentUtil.isAtLineStart(selEnd, editor.getDocument())) {
          selEnd--;
        }
        VisualPosition rangeStart = editor.offsetToVisualPosition(Math.min(selStart, selEnd));
        VisualPosition rangeEnd = editor.offsetToVisualPosition(Math.max(selStart, selEnd));
        final Couple<Integer> copiedRange =
          DuplicateAction.duplicateLinesRange(editor, editor.getDocument(), rangeStart, rangeEnd);
        if (copiedRange != null) {
          editor.getSelectionModel().setSelection(copiedRange.first, copiedRange.second);
        }
      }
      else {
        VisualPosition caretPos = editor.getCaretModel().getVisualPosition();
        DuplicateAction.duplicateLinesRange(editor, editor.getDocument(), caretPos, caretPos);
      }
    }
  }
}
