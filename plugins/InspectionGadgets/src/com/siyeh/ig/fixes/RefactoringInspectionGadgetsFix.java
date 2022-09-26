// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.RefactoringQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.PreviewableRefactoringActionHandler;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public abstract class RefactoringInspectionGadgetsFix extends InspectionGadgetsFix implements RefactoringQuickFix {

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) {
    doFix(descriptor.getPsiElement());
  }

  @Override
  public abstract @NotNull PreviewableRefactoringActionHandler getHandler();

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    PsiElement element = getElementToRefactor(previewDescriptor.getPsiElement());
    PreviewableRefactoringActionHandler handler = getHandler();
    return handler.generatePreview(project, element);
  }
}
