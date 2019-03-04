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
 * Emulates Emacs 'forward-paragraph' action
 */
public class ForwardParagraphAction extends EditorAction {
  public ForwardParagraphAction() {
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
