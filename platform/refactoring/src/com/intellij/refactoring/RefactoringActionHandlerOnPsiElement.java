// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;

public interface RefactoringActionHandlerOnPsiElement<E extends PsiElement> extends PreviewableRefactoringActionHandler {
  /**
   * @param project project
   * @param editor editor
   * @param element start element to invoke the action on
   */
  void invoke(Project project, Editor editor, E element);
}
