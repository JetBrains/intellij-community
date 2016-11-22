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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 20, 2002
 * Time: 6:21:42 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;

public class JoinLinesAction extends TextComponentEditorAction {
  public JoinLinesAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    public Handler() {
      super(true);
    }

    @Override
    public void executeWriteAction(Editor editor, Caret caret, DataContext dataContext) {
      final Document doc = editor.getDocument();

      LogicalPosition caretPosition = caret.getLogicalPosition();
      int startLine = caretPosition.line;
      int endLine = startLine + 1;
      if (caret.hasSelection()) {
        startLine = doc.getLineNumber(caret.getSelectionStart());
        endLine = doc.getLineNumber(caret.getSelectionEnd());
        if (doc.getLineStartOffset(endLine) == caret.getSelectionEnd()) endLine--;
      }

      int caretRestoreOffset = -1;

      for (int i = startLine; i < endLine; i++) {
        if (i >= doc.getLineCount() - 1) break;
        CharSequence text = doc.getCharsSequence();
        int end = doc.getLineEndOffset(startLine) + doc.getLineSeparatorLength(startLine);
        int start = end - doc.getLineSeparatorLength(startLine);
        while (start > 0 && (text.charAt(start) == ' ' || text.charAt(start) == '\t')) start--;
        if (caretRestoreOffset == -1) caretRestoreOffset = start + 1;
        while (end < doc.getTextLength() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) end++;
        doc.replaceString(start, end, " ");
      }

      if (caret.hasSelection()) {
        caret.moveToOffset(caret.getSelectionEnd());
      } else {
        if (caretRestoreOffset != -1) {
          caret.moveToOffset(caretRestoreOffset);
          editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          caret.removeSelection();
        }
      }
    }
  }
}
