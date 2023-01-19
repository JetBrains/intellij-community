// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
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
  protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
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
    PsiElement parent = previewDescriptor.getPsiElement().getParent();
    PsiMethod method = findMethodToFix(parent);
    if (method == null) return IntentionPreviewInfo.EMPTY;
    PsiFile file = method.getContainingFile();
    if (parent.getContainingFile() == file) {
      doMakeFinal(method);
      return IntentionPreviewInfo.DIFF;
    }
    PsiMethod copy = (PsiMethod)method.copy();
    doMakeFinal(copy);
    return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, file.getName(), method.getText(), copy.getText());
  }

  private static void doMakeFinal(PsiMethod method) {
    method.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
  }

  private static @Nullable PsiMethod findMethodToFix(PsiElement element) {
    if (element instanceof PsiMethod) {
      if (!IntentionPreviewUtils.prepareElementForWrite(element)) {
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
