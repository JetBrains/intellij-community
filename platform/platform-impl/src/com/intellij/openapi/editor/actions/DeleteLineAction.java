/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 14, 2002
 * Time: 7:18:30 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;

public class DeleteLineAction extends TextComponentEditorAction {
  public DeleteLineAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      CommandProcessor.getInstance().setCurrentCommandGroupId(EditorActionUtil.DELETE_COMMAND_GROUP);
      SelectionModel selectionModel = editor.getSelectionModel();

      if (selectionModel.hasSelection()) {
        Document document = editor.getDocument();
        int selectionStart = selectionModel.getSelectionStart();
        int selectionEnd = selectionModel.getSelectionEnd();
        selectionModel.removeSelection();
        int lineStartOffset = document.getLineStartOffset(document.getLineNumber(selectionStart));
        int lineEndOffset = document.getLineEndOffset(document.getLineNumber(selectionEnd));
        document.deleteString(lineStartOffset, lineEndOffset);
        return;
      }
      deleteLineAtCaret(editor);
    }
  }

  public static void deleteLineAtCaret(Editor editor) {
    LogicalPosition logicalPosition = editor.getCaretModel().getLogicalPosition();
    int lineNumber = logicalPosition.line;
    Document document = editor.getDocument();
    if (lineNumber >= document.getLineCount())
      return;

    if (lineNumber == document.getLineCount() - 1){
      if (document.getLineCount() > 0 && lineNumber > 0){
        int start = document.getLineEndOffset(lineNumber - 1);
        int end = document.getLineEndOffset(lineNumber) + document.getLineSeparatorLength(lineNumber);
        document.deleteString(start, end);
        LogicalPosition pos = new LogicalPosition(lineNumber - 1, logicalPosition.column);
        editor.getCaretModel().moveToLogicalPosition(pos);
      }
      else{
        document.deleteString(0, document.getTextLength());
        editor.getCaretModel().moveToOffset(0);
      }
    }
    else{
      VisualPosition caretPosition = editor.getCaretModel().getVisualPosition();
      VisualPosition thisLineVisible = new VisualPosition(caretPosition.line, 0);
      LogicalPosition thisLineLogical = editor.visualToLogicalPosition(thisLineVisible);
      VisualPosition nextLineVisible = new VisualPosition(caretPosition.line + 1, 0);
      LogicalPosition nextLineLogical = editor.visualToLogicalPosition(nextLineVisible);

      int startOffset = editor.logicalPositionToOffset(thisLineLogical);
      int endOffset = editor.logicalPositionToOffset(nextLineLogical);

      document.deleteString(startOffset, endOffset);
    }

    editor.getCaretModel().moveToLogicalPosition(logicalPosition);
    editor.getSelectionModel().removeSelection();
  }
}
