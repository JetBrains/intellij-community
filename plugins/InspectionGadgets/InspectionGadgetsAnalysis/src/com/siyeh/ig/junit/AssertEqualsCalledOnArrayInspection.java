/*
 * Copyright 2010-2012 Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.junit;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ImportUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AssertEqualsCalledOnArrayInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("assertequals.called.on.arrays.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("assertequals.called.on.arrays.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new AssertEqualsCalledOnArrayFix();
  }

  private static class AssertEqualsCalledOnArrayFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("assertequals.called.on.arrays.quickfix");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement methodNameIdentifier = descriptor.getPsiElement();
      final PsiElement parent = methodNameIdentifier.getParent();
      if (!(parent instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression methodExpression = (PsiReferenceExpression)parent;
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null && ImportUtils.addStaticImport("org.junit.Assert", "assertArrayEquals", methodExpression)) {
        PsiReplacementUtil.replaceExpression(methodExpression, "assertArrayEquals");
      }
      else {
        PsiReplacementUtil.replaceExpression(methodExpression, "org.junit.Assert.assertArrayEquals");
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssertEqualsOnArrayVisitor();
  }

  private static class AssertEqualsOnArrayVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"assertEquals".equals(methodName)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final PsiType type1;
      final PsiType type2;
      if (arguments.length == 2) {
        final PsiExpression argument0 = arguments[0];
        type1 = argument0.getType();
        final PsiExpression argument1 = arguments[1];
        type2 = argument1.getType();
      }
      else if (arguments.length == 3) {
        final PsiExpression argument0 = arguments[1];
        type1 = argument0.getType();
        final PsiExpression argument1 = arguments[2];
        type2 = argument1.getType();
      }
      else {
        return;
      }
      if (!(type1 instanceof PsiArrayType) || !(type2 instanceof PsiArrayType)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (!InheritanceUtil.isInheritor(containingClass, "junit.framework.Assert") &&
          !InheritanceUtil.isInheritor(containingClass, "org.junit.Assert") &&
          !InheritanceUtil.isInheritor(containingClass, "org.testng.AssertJUnit")) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}
