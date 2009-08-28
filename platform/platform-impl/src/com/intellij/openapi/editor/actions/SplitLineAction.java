package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.util.text.CharArrayUtil;

/**
 * @author max
 */
public class SplitLineAction extends EditorAction {
  public SplitLineAction() {
    super(new Handler());
    setEnabledInModalContext(false);
  }

  private static class Handler extends EditorWriteActionHandler {
    public boolean isEnabled(Editor editor, DataContext dataContext) {
      return getEnterHandler().isEnabled(editor, dataContext) &&
             !((EditorEx)editor).isEmbeddedIntoDialogWrapper();
    }

    public void executeWriteAction(Editor editor, DataContext dataContext) {
      LogicalPosition caretPosition = editor.getCaretModel().getLogicalPosition();

      final Document document = editor.getDocument();
      final CharSequence chars = document.getCharsSequence();

      int offset = editor.getCaretModel().getOffset();
      int lineStart = document.getLineStartOffset(document.getLineNumber(offset));

      final CharSequence beforeCaret = chars.subSequence(lineStart, offset);

      if (CharArrayUtil.containsOnlyWhiteSpaces(beforeCaret)) {
        String strToInsert = "";
        if (beforeCaret != null) {
          strToInsert +=  beforeCaret.toString();
        }
        strToInsert += "\n";
        document.insertString(lineStart, strToInsert);
        editor.getCaretModel().moveToOffset(offset);
      } else {
        getEnterHandler().execute(editor, dataContext);

        editor.getCaretModel().moveToLogicalPosition(caretPosition);
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }

    }

    private static EditorActionHandler getEnterHandler() {
      return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
    }
  }
}
