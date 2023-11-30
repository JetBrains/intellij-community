// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.NotNull;

public final class StartNewLineBeforeAction extends EditorAction {

  public StartNewLineBeforeAction() {
    super(new Handler());
  }

  private static final class Handler extends EditorWriteActionHandler.ForEachCaret {
    @Override
    public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      return getHandler(IdeActions.ACTION_EDITOR_ENTER).isEnabled(editor, caret, dataContext);
    }

    @Override
    public void executeWriteAction(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      editor.getSelectionModel().removeSelection();
      LogicalPosition caretPosition = editor.getCaretModel().getLogicalPosition();
      final int line = caretPosition.line;
      int lineStartOffset = editor.getDocument().getLineStartOffset(line);
      editor.getCaretModel().moveToOffset(lineStartOffset);
      getHandler(IdeActions.ACTION_EDITOR_ENTER).execute(editor, caret, dataContext);
      editor.getCaretModel().moveToOffset(editor.getDocument().getLineStartOffset(line));
      getHandler(IdeActions.ACTION_EDITOR_MOVE_LINE_END).execute(editor, caret, dataContext);
    }

    private static EditorActionHandler getHandler(@NotNull String actionId) {
      return EditorActionManager.getInstance().getActionHandler(actionId);
    }
  }
}
