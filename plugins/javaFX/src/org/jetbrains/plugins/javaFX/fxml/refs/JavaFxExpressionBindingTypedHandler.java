// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;

public final class JavaFxExpressionBindingTypedHandler extends TypedHandlerDelegate {

  @Override
  public @NotNull Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (!(file instanceof XmlFile)) return Result.CONTINUE;
    if (!JavaFxFileTypeFactory.isFxml(file.getVirtualFile())) return Result.CONTINUE;

    int offset = editor.getCaretModel().getOffset();
    if (offset < 1) return Result.CONTINUE;
    CharSequence text = editor.getDocument().getCharsSequence();

    if (c == '{') {
      // Trigger completion after "${"
      if (offset >= 2 && text.charAt(offset - 2) == '$') {
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
      }
      return Result.CONTINUE;
    }
    else if (c == ' ') {
      // Trigger completion inside a complex expression
      var previousChar = text.charAt(offset - 1);
      if (isExpressionTriggerChar(previousChar) && isInsideBinding(text, offset)) {
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
      }
    }
    return Result.CONTINUE;
  }

  /** Operator / paren / space chars after which a new operand identifier may follow. */
  private static boolean isExpressionTriggerChar(char c) {
    return c == '(' || c == ' '
           || c == '+' || c == '-' || c == '*' || c == '/' || c == '%'
           || c == '!' || c == '&' || c == '|'
           || c == '<' || c == '>' || c == '=';
  }

  /**
   * Checks whether the offset sits inside a <code>${...}</code> expression body in the current line:
   * a <code>${</code> appears earlier in the line and no closing <code>}</code> lies between it and the offset.
   */
  private static boolean isInsideBinding(@NotNull CharSequence text, int offset) {
    int lineStart = offset;
    while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;
    int lastOpen = -1;
    for (int i = lineStart; i < offset - 1; i++) {
      if (text.charAt(i) == '$' && text.charAt(i + 1) == '{') lastOpen = i;
      else if (text.charAt(i) == '}' && lastOpen != -1 && i > lastOpen) lastOpen = -1;
    }
    return lastOpen != -1;
  }
}
