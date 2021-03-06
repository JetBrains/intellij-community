// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import org.jetbrains.annotations.NotNull;

public class AddCaretPerSelectedLineAction extends EditorAction {
  public AddCaretPerSelectedLineAction() {
    super(new Handler());
  }

  private static final class Handler extends EditorActionHandler.ForEachCaret {
    @Override
    protected void doExecute(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      CaretModel caretModel = editor.getCaretModel();
      Document document = editor.getDocument();
      int selectionStart = caret.getSelectionStart();
      int startLine = document.getLineNumber(selectionStart);
      int selectionEnd = caret.getSelectionEnd();
      int endLine = document.getLineNumber(selectionEnd);
      if (endLine > startLine && selectionEnd == document.getLineStartOffset(endLine)) endLine--;

      if (caretModel.getCaretCount() + endLine - startLine > caretModel.getMaxCaretCount()) {
        EditorUtil.notifyMaxCarets(editor);
        return;
      }

      caret.removeSelection();

      boolean primary = caret.getOffset() != selectionStart;
      for (int i = startLine; i <= endLine; i++) {
        int targetOffset = document.getLineEndOffset(i);

        if (targetOffset == caret.getOffset()) {
          // move caret away, so that it doesn't prevent creating a new one
          // target offset doesn't matter, the caret is removed after the loop anyway
          caret.moveToOffset(targetOffset == 0 ? document.getTextLength() : 0);
        }

        caretModel.addCaret(editor.offsetToLogicalPosition(targetOffset), primary);
      }

      caretModel.removeCaret(caret);
    }
  }
}
