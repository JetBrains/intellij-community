// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MakeMethodFinalFix extends InspectionGadgetsFix {

  private final String myMethodName;

  public MakeMethodFinalFix(String methodName) {
    myMethodName = methodName;
  }

  @NotNull
  @Override
  public String getName() {
    return InspectionGadgetsBundle.message("make.method.final.fix.name", myMethodName);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("make.method.final.fix.family.name");
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement().getParent();
    PsiMethod method = findMethodToFix(element);
    if (method != null) {
      WriteAction.run(() -> method.getModifierList().setModifierProperty(PsiModifier.FINAL, true));
      if (isOnTheFly() && method.getContainingFile() != element.getContainingFile()) {
        method.navigate(true);
      }
    }
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    PsiMethod method = findMethodToFix(previewDescriptor.getPsiElement().getParent());
    if (method != null) {
      method.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
      return IntentionPreviewInfo.DIFF;
    }
    return IntentionPreviewInfo.EMPTY;
  }

  private static @Nullable PsiMethod findMethodToFix(PsiElement element) {
    if (element instanceof PsiMethod) {
      if (element.isPhysical() && !FileModificationService.getInstance().preparePsiElementsForWrite(element)) {
        return null;
      }
      return (PsiMethod)element;
    }
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)parent;
      final PsiMethod method = methodCall.resolveMethod();
      if (method == null || (element.isPhysical() && !FileModificationService.getInstance().preparePsiElementsForWrite(method))) {
        return null;
      }
      return method;
    }
    return null;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
