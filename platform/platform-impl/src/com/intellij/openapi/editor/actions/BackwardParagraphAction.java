package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.DocumentUtil.isLineEmpty;

/**
 * Emulates Emacs 'backward-paragraph' action
 */
public class BackwardParagraphAction extends EditorAction {
  public BackwardParagraphAction() {
    super(new Handler(false));
  }

  static class Handler extends EditorActionHandler {
    private final boolean myWithSelection;

    Handler(boolean withSelection) {
      super(true);
      myWithSelection = withSelection;
    }

    @Override
    protected void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      assert  caret != null;

      Document document = editor.getDocument();
      int currentLine = caret.getLogicalPosition().line;
      boolean atLineStart = caret.getLogicalPosition().column == 0;

      if (isLineEmpty(document, currentLine) || atLineStart) {
        while (--currentLine >= 0) {
          if (!isLineEmpty(document, currentLine)) {
            break;
          }
        }
      }

      while (--currentLine >= 0) {
        if (isLineEmpty(document, currentLine)) {
          break;
        }
      }

      int targetOffset = 0;
      if (currentLine >= 0) {
        int targetLineStart = document.getLineStartOffset(currentLine);
        int targetLineEnd = document.getLineEndOffset(currentLine);
        if (targetLineStart == targetLineEnd) {
          targetOffset = targetLineStart;
        }
        else {
          targetOffset = document.getLineStartOffset(currentLine + 1);
        }
      }

      EditorActionUtil.moveCaret(caret, targetOffset, myWithSelection);
    }
  }
}
