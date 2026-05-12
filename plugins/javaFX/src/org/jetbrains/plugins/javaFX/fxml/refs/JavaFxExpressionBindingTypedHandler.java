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
    if (c != '{') return Result.CONTINUE;
    if (!(file instanceof XmlFile)) return Result.CONTINUE;
    if (!JavaFxFileTypeFactory.isFxml(file.getVirtualFile())) return Result.CONTINUE;

    int offset = editor.getCaretModel().getOffset();
    if (offset < 2) return Result.CONTINUE;

    CharSequence text = editor.getDocument().getCharsSequence();
    if (text.charAt(offset - 2) == '$') {
      AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
    }
    return Result.CONTINUE;
  }
}
