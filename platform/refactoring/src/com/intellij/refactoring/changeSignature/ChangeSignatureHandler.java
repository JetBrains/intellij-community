// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Maxim.Medvedev
 */
public interface ChangeSignatureHandler extends RefactoringActionHandler {
  @Nullable
  default PsiElement findTargetMember(@NotNull PsiFile file, @NotNull Editor editor) {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    return element != null ? findTargetMember(element) : null;
  }

  @Nullable
  PsiElement findTargetMember(@NotNull PsiElement element);

  @Override
  void invoke(@NotNull Project project, PsiElement @NotNull [] elements, @Nullable DataContext dataContext);

  @Nullable @NlsContexts.DialogMessage
  String getTargetNotFoundMessage();
}
