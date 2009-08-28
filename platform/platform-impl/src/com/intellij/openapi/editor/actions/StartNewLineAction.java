package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;

/**
 * @author max
 */
public class StartNewLineAction extends EditorAction {
  public StartNewLineAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    public boolean isEnabled(Editor editor, DataContext dataContext) {
      return getEnterHandler().isEnabled(editor, dataContext);
    }

    public void executeWriteAction(Editor editor, DataContext dataContext) {
      if (editor.getDocument().getLineCount() != 0) {
        editor.getSelectionModel().removeSelection();
        LogicalPosition caretPosition = editor.getCaretModel().getLogicalPosition();
        int lineEndOffset = editor.getDocument().getLineEndOffset(caretPosition.line);
        editor.getCaretModel().moveToOffset(lineEndOffset);
      }

      getEnterHandler().execute(editor, dataContext);
    }

    private EditorActionHandler getEnterHandler() {
      return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
    }
  }
}
