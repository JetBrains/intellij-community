// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractPermuteLinesHandler extends EditorWriteActionHandler {
  @Override
  public void executeWriteAction(Editor editor, @Nullable Caret caret, DataContext dataContext) {
    if (caret == null) caret = editor.getCaretModel().getPrimaryCaret();
    Document document = editor.getDocument();
    int lastLine = document.getLineCount() - 1;
    boolean hasSelection = caret.hasSelection();
    int startLine;
    int endLine;
    if (hasSelection) {
      startLine = document.getLineNumber(caret.getSelectionStart());
      int selectionEnd = caret.getSelectionEnd();
      endLine = document.getLineNumber(selectionEnd);
      if (selectionEnd == document.getLineStartOffset(endLine)) {
        endLine--;
      }
    }
    else {
      startLine = 0;
      endLine = lastLine;
    }
    if (startLine >= endLine) {
      return;
    }
    int lineCount = endLine - startLine + 1;
    String[] lines = new String[lineCount];
    for (int i = 0; i < lineCount; i++) {
      int line = i + startLine;
      lines[i] = document.getText(new TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)));
    }
    String caretLineContent = null;
    int caretOffsetInLine = 0;
    if (!hasSelection) {
      int caretLine = caret.getLogicalPosition().line;
      caretLineContent = lines[caretLine - startLine];
      caretOffsetInLine = caret.getOffset() - document.getLineStartOffset(caretLine);
    }
    permute(lines);
    String newContent = String.join("\n", lines);
    int toReplaceStart = document.getLineStartOffset(startLine);
    int toReplaceEnd = document.getLineEndOffset(endLine);
    document.replaceString(toReplaceStart, toReplaceEnd, newContent);
    if (hasSelection) {
      int selectionEnd = endLine < lastLine ? document.getLineStartOffset(endLine + 1) : document.getLineEndOffset(endLine);
      caret.moveToOffset(selectionEnd);
      caret.setSelection(toReplaceStart, selectionEnd);
    }
    else {
      for (int i = 0; i < lineCount; i++) {
        if (lines[i] == caretLineContent) {
          caret.moveToOffset(document.getLineStartOffset(startLine + i) + caretOffsetInLine);
          break;
        }
      }
    }
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  public abstract void permute(@NotNull String[] lines);
}
