// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.editor;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class HandlerUtils {
  private HandlerUtils() {
  }

  public static boolean isReadOnly(final @NotNull Editor editor) {
    if (editor.isViewer()) {
      return true;
    }
    Document document = editor.getDocument();
    return !document.isWritable();
  }

  public static boolean canBeInvoked(final Editor editor, final Project project) {
    if (isReadOnly(editor)) {
      return false;
    }
    if (getPsiFile(editor, project) == null) {
      return false;
    }

    return true;
  }

  public static PsiFile getPsiFile(final @NotNull Editor editor, final Project project) {
    return PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
  }
}
