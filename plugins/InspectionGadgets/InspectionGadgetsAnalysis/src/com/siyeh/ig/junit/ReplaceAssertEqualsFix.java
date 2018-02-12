/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ig.junit;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ImportUtils;
import org.jetbrains.annotations.NotNull;

class ReplaceAssertEqualsFix extends InspectionGadgetsFix {
  private final String myMethodName;

  public ReplaceAssertEqualsFix(String methodName) {
    myMethodName = methodName;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("replace.assertequals.quickfix", myMethodName);
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiReferenceExpression)) {
      return;
    }
    final PsiReferenceExpression methodExpression = (PsiReferenceExpression)parent;
    final PsiElement grandParent = methodExpression.getParent();
    if (!(grandParent instanceof PsiMethodCallExpression)) {
      return;
    }
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) {
      return;
    }
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) {
      return;
    }
    final String className = containingClass.getQualifiedName();
    if (className == null) {
      return;
    }
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (qualifier == null && ImportUtils.addStaticImport(className, myMethodName, methodExpression)) {
      PsiReplacementUtil.replaceExpression(methodExpression, myMethodName, new CommentTracker());
    }
    else {
      PsiReplacementUtil.replaceExpression(methodExpression, StringUtil.getQualifiedName(className, myMethodName), new CommentTracker());
    }
  }
}
