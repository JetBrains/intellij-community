// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.DocumentUtil.isLineEmpty;

/**
 * Emulates Emacs 'forward-paragraph' action
 */
@ApiStatus.Internal
public final class ForwardParagraphAction extends EditorAction {
  public ForwardParagraphAction() {
    super(new Handler(false));
  }

  static final class Handler extends EditorActionHandler.ForEachCaret {
    private final boolean myWithSelection;

    Handler(boolean withSelection) {
      myWithSelection = withSelection;
    }

    @Override
    protected void doExecute(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      Document document = editor.getDocument();
      int currentLine = caret.getLogicalPosition().line;
      int lineCount = document.getLineCount();

      if (isLineEmpty(document, currentLine)) {
        while (++currentLine < lineCount) {
          if (!isLineEmpty(document, currentLine)) {
            break;
          }
        }
      }

      int targetOffset = document.getTextLength();
      while (++currentLine < lineCount) {
        if (isLineEmpty(document, currentLine)) {
          targetOffset = document.getLineStartOffset(currentLine);
          break;
        }
      }

      EditorActionUtil.moveCaret(caret, targetOffset, myWithSelection);
    }
  }
}
