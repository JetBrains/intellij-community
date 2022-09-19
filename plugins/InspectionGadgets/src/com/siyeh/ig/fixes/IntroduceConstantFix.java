// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceField.IntroduceConstantHandlerBase;
import org.jetbrains.annotations.NotNull;

public class IntroduceConstantFix extends RefactoringInspectionGadgetsFix {

  private final @NlsActions.ActionText String myFamilyName;

  public IntroduceConstantFix() { 
    myFamilyName = RefactoringBundle.message("introduce.constant.title");
  }

  public IntroduceConstantFix(@NlsActions.ActionText String familyName) {
    myFamilyName = familyName;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return myFamilyName;
  }

  @NotNull
  @Override
  public RefactoringActionHandler getHandler() {
    return JavaRefactoringActionHandlerFactory.getInstance().createIntroduceConstantHandler();
  }

  @Override
  public PsiElement getElementToRefactor(PsiElement element) {
    return element;
  }

  @Override
  public void doFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (!(element instanceof PsiExpression)) return;

    doIntroduce(project, (PsiExpression)element);
  }

  protected void doIntroduce(@NotNull Project project, PsiExpression element) {
    PsiExpression[] expressions = {element};
    var handler = (IntroduceConstantHandlerBase)getHandler();
    handler.invoke(project, expressions);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    PsiElement element = previewDescriptor.getPsiElement();
    if (!(element instanceof PsiExpression)) return IntentionPreviewInfo.EMPTY;
    applyFix(project, previewDescriptor);
    return IntentionPreviewInfo.DIFF;
  }
}
