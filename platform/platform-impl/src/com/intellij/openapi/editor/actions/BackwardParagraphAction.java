package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Emulates Emacs 'backward-paragraph' action
 */
public class BackwardParagraphAction extends EditorAction {
  public BackwardParagraphAction() {
    super(new MyHandler());
  }

  private static class MyHandler extends EditorActionHandler {
    private MyHandler() {
      super(true);
    }

    @Override
    protected void doExecute(Editor editor, @Nullable Caret caret, DataContext dataContext) {
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

      caret.removeSelection();
      caret.moveToOffset(targetOffset);
      EditorModificationUtil.scrollToCaret(editor);
    }

    private static boolean isLineEmpty(Document document, int line) {
      return StringUtil.equalsIgnoreWhitespaces(
        document.getImmutableCharSequence().subSequence(document.getLineStartOffset(line), document.getLineEndOffset(line)), "");
    }
  }
}
