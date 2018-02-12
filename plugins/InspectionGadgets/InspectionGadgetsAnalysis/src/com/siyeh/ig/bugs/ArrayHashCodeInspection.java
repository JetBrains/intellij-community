/*
 * Copyright 2011-2018 Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ArrayHashCodeInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("array.hash.code.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "array.hash.code.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiArrayType type = (PsiArrayType)infos[0];
    if (type.getComponentType() instanceof PsiArrayType) {
      return new ArrayHashCodeFix(true);
    }
    return new ArrayHashCodeFix(false);
  }

  private static class ArrayHashCodeFix extends InspectionGadgetsFix {

    private final boolean deepHashCode;

    public ArrayHashCodeFix(boolean deepHashCode) {
      this.deepHashCode = deepHashCode;
    }

    @Override
    @NotNull
    public String getName() {
      if (deepHashCode) {
        return InspectionGadgetsBundle.message(
          "arrays.deep.hash.code.quickfix");
      }
      else {
        return InspectionGadgetsBundle.message(
          "arrays.hash.code.quickfix");
      }
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with implicit 'hashCode'";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor){
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      @NonNls final StringBuilder newExpressionText = new StringBuilder();
      if (deepHashCode) {
        newExpressionText.append("java.util.Arrays.deepHashCode(");
      }
      else {
        newExpressionText.append("java.util.Arrays.hashCode(");
      }
      CommentTracker commentTracker = new CommentTracker();
      newExpressionText.append(commentTracker.text(qualifier));
      newExpressionText.append(')');
      PsiReplacementUtil.replaceExpressionAndShorten(methodCallExpression, newExpressionText.toString(), commentTracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ArrayHashCodeVisitor();
  }

  private static class ArrayHashCodeVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.HASH_CODE.equals(methodName)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 0) {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final PsiType type = qualifier.getType();
      if (!(type instanceof PsiArrayType)) {
        return;
      }
      registerMethodCallError(expression, type);
    }
  }
}
