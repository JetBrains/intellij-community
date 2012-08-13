package org.jetbrains.android.util;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
* @author Eugene.Kudelevsky
*/
public class HintBasedErrorReporter implements ErrorReporter {
  private final Editor myEditor;

  public HintBasedErrorReporter(@NotNull Editor editor) {
    myEditor = editor;
  }

  @Override
  public void report(@NotNull String message, @NotNull String title) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      throw new IncorrectOperationException(message);
    }
    HintManager.getInstance().showErrorHint(myEditor, message);
  }
}
