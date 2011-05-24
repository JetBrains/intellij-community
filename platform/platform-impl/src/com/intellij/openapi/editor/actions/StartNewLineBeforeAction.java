package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
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
    public boolean isEnabled(Editor editor, DataContext dataContext) {
      return getHandler(IdeActions.ACTION_EDITOR_ENTER).isEnabled(editor, dataContext);
    }

    public void executeWriteAction(Editor editor, DataContext dataContext) {
      editor.getSelectionModel().removeSelection();
      LogicalPosition caretPosition = editor.getCaretModel().getLogicalPosition();
      final int line = caretPosition.line;
      int lineStartOffset = editor.getDocument().getLineStartOffset(line);
      editor.getCaretModel().moveToOffset(lineStartOffset);
      getHandler(IdeActions.ACTION_EDITOR_ENTER).execute(editor, dataContext);
      editor.getCaretModel().moveToOffset(editor.getDocument().getLineStartOffset(line));
      getHandler(IdeActions.ACTION_EDITOR_MOVE_LINE_END).execute(editor, dataContext);
    }

    private static EditorActionHandler getHandler(@NotNull String actionId) {
      return EditorActionManager.getInstance().getActionHandler(actionId);
    }
  }
}
