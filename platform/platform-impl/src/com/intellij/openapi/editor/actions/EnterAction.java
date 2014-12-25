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
 * Date: May 13, 2002
 * Time: 9:35:30 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.util.ui.MacUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EnterAction extends EditorAction {
  public EnterAction() {
    super(new Handler());
    setInjectedContext(true);
  }

  private static class Handler extends EditorWriteActionHandler {
    public Handler() {
      super(true);
    }

    @Override
    public void executeWriteAction(Editor editor, @Nullable Caret caret, DataContext dataContext) {
      CommandProcessor.getInstance().setCurrentCommandName(EditorBundle.message("typing.command.name"));
      insertNewLineAtCaret(editor);
    }

    @Override
    public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      return !editor.isOneLineMode();
    }
  }

  public static void insertNewLineAtCaret(Editor editor) {
    MacUIUtil.hideCursor();
    Document document = editor.getDocument();
    int caretLine = editor.getCaretModel().getLogicalPosition().line;
    if(!editor.isInsertMode()) {
      int lineCount = document.getLineCount();
      if(caretLine < lineCount) {
        if (caretLine == lineCount - 1) {
          document.insertString(document.getTextLength(), "\n");
        }
        LogicalPosition pos = new LogicalPosition(caretLine + 1, 0);
        editor.getCaretModel().moveToLogicalPosition(pos);
        editor.getSelectionModel().removeSelection();
        EditorModificationUtil.scrollToCaret(editor);
      }
      return;
    }
    EditorModificationUtil.deleteSelectedText(editor);
    // Smart indenting here:
    CharSequence text = document.getCharsSequence();

    int indentLineNum = caretLine;
    int lineLength = 0;
    if (document.getLineCount() > 0) {
      for(;indentLineNum >= 0; indentLineNum--) {
        lineLength = document.getLineEndOffset(indentLineNum) - document.getLineStartOffset(indentLineNum);
        if(lineLength > 0)
          break;
      }
    } else {
      indentLineNum = -1;
    }

    int colNumber = editor.getCaretModel().getLogicalPosition().column;
    StringBuilder buf = new StringBuilder();
    if(indentLineNum >= 0) {
      int lineStartOffset = document.getLineStartOffset(indentLineNum);
      for(int i = 0; i < lineLength; i++) {
        char c = text.charAt(lineStartOffset + i);
        if(c != ' ' && c != '\t') {
          break;
        }
        if(i >= colNumber) {
          break;
        }
        buf.append(c);
      }
    }
    int caretOffset = editor.getCaretModel().getOffset();
    String s = "\n"+buf;
    document.insertString(caretOffset, s);
    editor.getCaretModel().moveToOffset(caretOffset + s.length());
    EditorModificationUtil.scrollToCaret(editor);
    editor.getSelectionModel().removeSelection();
  }
}
