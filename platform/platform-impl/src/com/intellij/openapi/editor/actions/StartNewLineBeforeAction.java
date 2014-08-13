/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 5/19/11 10:42 AM
 */
public class StartNewLineBeforeAction extends EditorAction {

  public StartNewLineBeforeAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    public Handler() {
      super(true);
    }

    @Override
    public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      return getHandler(IdeActions.ACTION_EDITOR_ENTER).isEnabled(editor, caret, dataContext);
    }

    @Override
    public void executeWriteAction(Editor editor, Caret caret, DataContext dataContext) {
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
