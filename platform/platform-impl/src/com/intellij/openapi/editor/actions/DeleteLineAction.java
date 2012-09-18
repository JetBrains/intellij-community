/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
    @Override
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      CommandProcessor.getInstance().setCurrentCommandGroupId(EditorActionUtil.DELETE_COMMAND_GROUP);
      SelectionModel selectionModel = editor.getSelectionModel();
      Document document = editor.getDocument();

      if (selectionModel.hasSelection()) {
        int selectionStart = selectionModel.getSelectionStart();
        int selectionEnd = selectionModel.getSelectionEnd();
        selectionModel.removeSelection();
        int lineStartOffset = document.getLineStartOffset(document.getLineNumber(selectionStart));
        int nextLine = document.getLineNumber(selectionEnd);
        if (document.getLineStartOffset(nextLine) != selectionEnd) {
          // There is a possible case that selection ends at the line start, i.e. something like below ([...] denotes selected text,
          // '|' is a line start):
          //   |line 1
          //   |[line 2
          //   |]line 3
          // We don't want to delete line 3 here. However, the situation below is different:
          //   |line 1
          //   |[line 2
          //   |line] 3
          // Line 3 must be removed here.
          nextLine++;
        }
        int nextLineStartOffset = nextLine == document.getLineCount()
                                  ? document.getTextLength()
                                  : Math.min(document.getTextLength(), document.getLineStartOffset(nextLine));
        document.deleteString(lineStartOffset, nextLineStartOffset);
        return;
      }
      VisualPosition position = editor.getCaretModel().getVisualPosition();
      selectionModel.selectLineAtCaret();
      boolean removeLastSymbol = selectionModel.getSelectionEnd() == document.getTextLength() && document.getLineCount() > 1;
      EditorModificationUtil.deleteSelectedText(editor);
      if (removeLastSymbol) {
        document.deleteString(document.getTextLength() - 1, document.getTextLength());
        position = new VisualPosition(position.line - 1, position.column);
      }
      editor.getCaretModel().moveToVisualPosition(position);
    }
  }
}
