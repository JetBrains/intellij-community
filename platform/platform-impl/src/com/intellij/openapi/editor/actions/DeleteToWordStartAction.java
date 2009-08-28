/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 14, 2002
 * Time: 7:18:30 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.actionSystem.DataContext;

public class DeleteToWordStartAction extends TextComponentEditorAction {
  public DeleteToWordStartAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      CommandProcessor.getInstance().setCurrentCommandGroupId(EditorActionUtil.DELETE_COMMAND_GROUP);
      deleteToWordStart(editor);
    }
  }

  private static void deleteToWordStart(Editor editor) {
    int endOffset = editor.getCaretModel().getOffset();
    EditorActionUtil.moveCaretToPreviousWord(editor, false);
    int startOffset = editor.getCaretModel().getOffset();
    Document document = editor.getDocument();
    document.deleteString(startOffset, endOffset);
  }
}
