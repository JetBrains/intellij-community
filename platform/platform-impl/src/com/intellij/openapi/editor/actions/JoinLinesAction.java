// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;

public final class JoinLinesAction extends TextComponentEditorAction {
  public JoinLinesAction() {
    super(new Handler());
  }

  private static final class Handler extends EditorWriteActionHandler.ForEachCaret {
    @Override
    public void executeWriteAction(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      final Document doc = editor.getDocument();

      LogicalPosition caretPosition = caret.getLogicalPosition();
      int startLine = caretPosition.line;
      int endLine = startLine + 1;
      if (caret.hasSelection()) {
        startLine = doc.getLineNumber(caret.getSelectionStart());
        endLine = doc.getLineNumber(caret.getSelectionEnd());
        if (doc.getLineStartOffset(endLine) == caret.getSelectionEnd()) endLine--;
      }

      int[] caretRestoreOffset = new int[]{-1};
      int lineCount = endLine - startLine;
      final int line = startLine;

      DocumentUtil.executeInBulk(doc, lineCount > 1000, () -> {
        for (int i = 0; i < lineCount; i++) {
          if (line >= doc.getLineCount() - 1) break;
          CharSequence text = doc.getCharsSequence();
          int end = doc.getLineEndOffset(line) + doc.getLineSeparatorLength(line);
          int start = end - doc.getLineSeparatorLength(line);
          while (start > 0 && (text.charAt(start) == ' ' || text.charAt(start) == '\t')) start--;
          if (caretRestoreOffset[0] == -1) caretRestoreOffset[0] = start + 1;
          while (end < doc.getTextLength() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) end++;
          doc.replaceString(start, end, " ");
        }
      });

      if (caret.hasSelection()) {
        caret.moveToOffset(caret.getSelectionEnd());
      }
      else {
        if (caretRestoreOffset[0] != -1) {
          caret.moveToOffset(caretRestoreOffset[0]);
          editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          caret.removeSelection();
        }
      }
    }
  }
}
