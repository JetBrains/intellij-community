// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.util.EditorUIUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.text.CharArrayUtil;
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
    EditorUIUtil.hideCursorInEditor(editor);
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
    int caretOffset = editor.getCaretModel().getOffset();
    String s;
    if (PlatformUtils.isGoIde()) {
      int lineStartOffset = document.getLineStartOffset(caretLine);
      int lineStartWsEndOffset = CharArrayUtil.shiftForward(text, lineStartOffset, " \t");
      s = "\n" + text.subSequence(lineStartOffset, Math.min(caretOffset, lineStartWsEndOffset));
    }
    else {
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
      s = "\n" + buf;
    }
    document.insertString(caretOffset, s);
    editor.getCaretModel().moveToOffset(caretOffset + s.length());
    EditorModificationUtil.scrollToCaret(editor);
    editor.getSelectionModel().removeSelection();
  }
}
