// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

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
    final PsiElement element = descriptor.getPsiElement().getParent();
    if (!(element instanceof PsiMethod)) {
      return;
    }
    final PsiMethod method = (PsiMethod)element;
    final PsiModifierList modifierList = method.getModifierList();
    modifierList.setModifierProperty(PsiModifier.FINAL, true);
  }
}
