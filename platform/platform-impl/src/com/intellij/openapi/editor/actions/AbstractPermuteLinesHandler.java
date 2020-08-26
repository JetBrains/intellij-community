// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractPermuteLinesHandler extends EditorWriteActionHandler {
  @Override
  protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    return getTargetLineRange(caret) != null;
  }

  @Override
  public void executeWriteAction(Editor editor, @Nullable Caret caret, DataContext dataContext) {
    if (caret == null) caret = editor.getCaretModel().getPrimaryCaret();
    Document document = editor.getDocument();
    Couple<Integer> lineRange = getTargetLineRange(caret);
    if (lineRange == null) {
      return;
    }
    int startLine = lineRange.first;
    int endLine = lineRange.second;
    int lineCount = endLine - startLine + 1;
    String[] lines = new String[lineCount];
    for (int i = 0; i < lineCount; i++) {
      int line = i + startLine;
      lines[i] = document.getText(new TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)));
    }
    boolean hasSelection = caret.hasSelection();
    String caretLineContent = null;
    int caretOffsetInLine = 0;
    if (!hasSelection) {
      int caretLine = caret.getLogicalPosition().line;
      if (caretLine <= endLine) {
        caretLineContent = lines[caretLine - startLine];
        caretOffsetInLine = caret.getOffset() - document.getLineStartOffset(caretLine);
      }
    }
    permute(lines);
    String newContent = String.join("\n", lines);
    int toReplaceStart = document.getLineStartOffset(startLine);
    int toReplaceEnd = document.getLineEndOffset(endLine);
    document.replaceString(toReplaceStart, toReplaceEnd, newContent);
    if (hasSelection) {
      int selectionEnd = endLine < document.getLineCount() - 1 ? document.getLineStartOffset(endLine + 1) : document.getTextLength();
      caret.moveToOffset(selectionEnd);
      caret.setSelection(toReplaceStart, selectionEnd);
    }
    else if (caretLineContent != null) {
      for (int i = 0; i < lineCount; i++) {
        if (lines[i] == caretLineContent) {
          caret.moveToOffset(document.getLineStartOffset(startLine + i) + caretOffsetInLine);
          break;
        }
      }
    }
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  @Nullable
  private static Couple<Integer> getTargetLineRange(@NotNull Caret caret) {
    Document document = caret.getEditor().getDocument();
    int startOffset = caret.hasSelection() ? caret.getSelectionStart() : 0;
    int endOffset = caret.hasSelection() ? caret.getSelectionEnd() : document.getTextLength();
    int startLine = document.getLineNumber(startOffset);
    int endLine = document.getLineNumber(endOffset);
    if (endOffset == document.getLineStartOffset(endLine)) {
      endLine--;
    }
    return startLine < endLine ? Couple.of(startLine, endLine) : null;
  }

  public abstract void permute(String @NotNull [] lines);
}
