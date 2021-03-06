// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.editor;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author ilyas
 */
public final class HandlerUtils {
  private HandlerUtils() {
  }

  public static boolean isReadOnly(@NotNull final Editor editor) {
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

  public static PsiFile getPsiFile(@NotNull final Editor editor, final Project project) {
    return PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
  }
}
