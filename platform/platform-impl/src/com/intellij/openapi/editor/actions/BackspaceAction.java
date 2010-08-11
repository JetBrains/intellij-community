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
 * Date: May 13, 2002
 * Time: 8:26:04 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;

public class BackspaceAction extends EditorAction {
  public BackspaceAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      CommandProcessor.getInstance().setCurrentCommandGroupId(EditorActionUtil.DELETE_COMMAND_GROUP);
      final SelectionModel selectionModel = editor.getSelectionModel();
      if (selectionModel.hasBlockSelection()) {
        final LogicalPosition start = selectionModel.getBlockStart();
        final LogicalPosition end = selectionModel.getBlockEnd();
        int column = Math.min(start.column, end.column);
        int startLine = Math.min(start.line, end.line);
        int endLine = Math.max(start.line, end.line);
        EditorModificationUtil.deleteBlockSelection(editor);
        if (column > 0 && start.column == end.column) {
          for (int i = startLine; i <= endLine; i++) {
            editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(i, column));
            doBackSpaceAtCaret(editor);
          }
          column--;
        }
        final int newColumn = Math.max(column, 0);
        selectionModel.setBlockSelection(new LogicalPosition(startLine, newColumn), new LogicalPosition(endLine, newColumn));
        return;
      }

      doBackSpaceAtCaret(editor);
    }
  }

  public static void doBackSpaceAtCaret(Editor editor) {
    if(editor.getSelectionModel().hasSelection()) {
      int newOffset = editor.getSelectionModel().getSelectionStart();
      editor.getCaretModel().moveToOffset(newOffset);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      EditorModificationUtil.deleteSelectedText(editor);
      return;
    }

    int lineNumber = editor.getCaretModel().getLogicalPosition().line;
    int colNumber = editor.getCaretModel().getLogicalPosition().column;
    Document document = editor.getDocument();
    if(colNumber > 0) {
      if(EditorModificationUtil.calcAfterLineEnd(editor) > 0) {
        int columnShift = -1;
        editor.getCaretModel().moveCaretRelatively(columnShift, 0, false, false, true);
      }
      else {
        int offset = editor.getCaretModel().getOffset();
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        editor.getSelectionModel().removeSelection();
        document.deleteString(offset-1, offset);
        editor.getCaretModel().moveToOffset(offset - 1, true);
      }
    }
    else if(lineNumber > 0) {
      int separatorLength = document.getLineSeparatorLength(lineNumber - 1);
      int lineEnd = document.getLineEndOffset(lineNumber - 1) + separatorLength;
      editor.getCaretModel().moveToOffset(lineEnd - separatorLength);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().removeSelection();
      document.deleteString(lineEnd - separatorLength, lineEnd);
      // Do not group delete newline and other deletions.
      CommandProcessor commandProcessor = CommandProcessor.getInstance();
      commandProcessor.setCurrentCommandGroupId(null);
    }
  }
}
