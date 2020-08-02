// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.ide.CopyPasteManager;
import org.jetbrains.annotations.NotNull;

public class StartNewLineAction extends EditorAction {
  public StartNewLineAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler.ForEachCaret {
    @Override
    public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      return getEnterHandler().isEnabled(editor, caret, dataContext);
    }

    @Override
    public void executeWriteAction(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      CopyPasteManager.getInstance().stopKillRings();
      if (editor.getDocument().getLineCount() != 0) {
        editor.getSelectionModel().removeSelection();
        LogicalPosition caretPosition = editor.getCaretModel().getLogicalPosition();
        int lineEndOffset = editor.getDocument().getLineEndOffset(caretPosition.line);
        editor.getCaretModel().moveToOffset(lineEndOffset);
      }

      getEnterHandler().execute(editor, caret, dataContext);
    }

    private static EditorActionHandler getEnterHandler() {
      return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
    }
  }
}
