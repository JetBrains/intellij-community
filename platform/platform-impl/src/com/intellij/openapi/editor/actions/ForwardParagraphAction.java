package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Emulates Emacs 'forward-paragraph' action
 */
public class ForwardParagraphAction extends EditorAction {
  public ForwardParagraphAction() {
    super(new MyHandler());
  }

  private static class MyHandler extends EditorActionHandler {
    private MyHandler() {
      super(true);
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
