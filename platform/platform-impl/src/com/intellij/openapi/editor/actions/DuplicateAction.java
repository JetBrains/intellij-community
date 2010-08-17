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
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;

public class DuplicateAction extends EditorAction {
  public DuplicateAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      duplicateLineOrSelectedBlockAtCaret(editor);
    }

    public boolean isEnabled(Editor editor, DataContext dataContext) {
      return !editor.isOneLineMode() || editor.getSelectionModel().hasSelection();
    }
  }

  private static void duplicateLineOrSelectedBlockAtCaret(Editor editor) {
    Document document = editor.getDocument();
    CaretModel caretModel = editor.getCaretModel();
    ScrollingModel scrollingModel = editor.getScrollingModel();
    if(editor.getSelectionModel().hasSelection()) {
      int start = editor.getSelectionModel().getSelectionStart();
      int end = editor.getSelectionModel().getSelectionEnd();
      String s = document.getCharsSequence().subSequence(start, end).toString();
      document.insertString(end, s);
      caretModel.moveToOffset(end+s.length());
      scrollingModel.scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().removeSelection();
      editor.getSelectionModel().setSelection(end, end+s.length());
    }
    else {
      VisualPosition caret = caretModel.getVisualPosition();
      int offset = caretModel.getOffset();
      int visualLine = caret.line;

      LogicalPosition lineStart = editor.visualToLogicalPosition(new VisualPosition(visualLine, 0));
      while (lineStart.softWrapLinesOnCurrentLogicalLine > 0) {
        lineStart = editor.visualToLogicalPosition(new VisualPosition(--visualLine, 0));
      }

      visualLine = caret.line + 1;
      LogicalPosition nextLineStart = editor.visualToLogicalPosition(new VisualPosition(caret.line + 1, 0));
      while (nextLineStart.line == lineStart.line) {
        nextLineStart = editor.visualToLogicalPosition(new VisualPosition(++visualLine, 0));
      }
      int start = editor.logicalPositionToOffset(lineStart);
      int end = editor.logicalPositionToOffset(nextLineStart);
      String s = document.getCharsSequence().subSequence(start, end).toString();
      final int lineToCheck = nextLineStart.line - 1;

      int newOffset = end + offset - start;
      if(lineToCheck == document.getLineCount () /*empty document*/ ||
         document.getLineSeparatorLength(lineToCheck) == 0) {
        s = "\n"+s;
        newOffset++;
      }
      document.insertString(end, s);

      caretModel.moveToOffset(newOffset);
      scrollingModel.scrollToCaret(ScrollType.RELATIVE);
    }
  }

  @Override
  public void update(final Editor editor, final Presentation presentation, final DataContext dataContext) {
    super.update(editor, presentation, dataContext);
    if (editor.getSelectionModel().hasSelection()) {
      presentation.setText(EditorBundle.message("action.duplicate.block"), true);
    }
    else {
      presentation.setText(EditorBundle.message("action.duplicate.line"), true);
    }
  }
}
