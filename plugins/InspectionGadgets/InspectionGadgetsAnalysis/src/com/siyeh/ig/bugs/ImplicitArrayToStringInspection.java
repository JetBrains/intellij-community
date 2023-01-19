/*
 * Copyright 2007-2019 Bas Leijdekkers
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
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImplicitArrayToStringInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    if (((Boolean)infos[1]).booleanValue()) {
      return InspectionGadgetsBundle.message(
        "explicit.array.to.string.problem.descriptor");
    }
    else if (infos[0] instanceof PsiMethodCallExpression) {
      return InspectionGadgetsBundle.message(
        "implicit.array.to.string.method.call.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message(
        "implicit.array.to.string.problem.descriptor");
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    final boolean removeToString = ((Boolean)infos[1]).booleanValue();
    final PsiArrayType type = (PsiArrayType)expression.getType();
    if (type != null) {
      final PsiType componentType = type.getComponentType();
      if (componentType instanceof PsiArrayType) {
        return new ImplicitArrayToStringFix(true, removeToString);
      }
    }
    return new ImplicitArrayToStringFix(false, removeToString);
  }

  private static class ImplicitArrayToStringFix extends InspectionGadgetsFix {

    private final boolean deepString;
    private final boolean removeToString;

    ImplicitArrayToStringFix(boolean deepString, boolean removeToString) {
      this.deepString = deepString;
      this.removeToString = removeToString;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("implicit.array.to.string.fix.family.name");
    }

    @Override
    @NotNull
    public String getName() {
      @NonNls final String expressionText;
      if (deepString) {
        expressionText = "java.util.Arrays.deepToString()";
      }
      else {
        expressionText = "java.util.Arrays.toString()";
      }
      return InspectionGadgetsBundle.message(
        "implicit.array.to.string.quickfix", expressionText);
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor){
      final PsiElement element = descriptor.getPsiElement();
      final PsiExpression expression;
      if (element instanceof PsiExpression) {
        expression = (PsiExpression)element;
      }
      else {
        expression = (PsiExpression)element.getParent().getParent();
      }
      CommentTracker commentTracker = new CommentTracker();
      final String expressionText;
      if (removeToString) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (qualifier == null) {
          return;
        }
        expressionText = commentTracker.text(qualifier);
      }
      else {
        expressionText = commentTracker.text(expression);
      }
      @NonNls final String newExpressionText;
      if (deepString) {
        newExpressionText =
          "java.util.Arrays.deepToString(" + expressionText + ')';
      }
      else {
        newExpressionText =
          "java.util.Arrays.toString(" + expressionText + ')';
      }
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiExpressionList) {
        final PsiElement grandParent = parent.getParent();
        if (grandParent instanceof PsiMethodCallExpression) {
          final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
          final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
          if ("valueOf".equals(methodExpression.getReferenceName())) {
            PsiReplacementUtil.replaceExpressionAndShorten(methodCallExpression, newExpressionText, commentTracker);
            return;
          }
        }
      }
      PsiReplacementUtil.replaceExpressionAndShorten(expression, newExpressionText, commentTracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ImplicitArrayToStringVisitor();
  }

  private static class ImplicitArrayToStringVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      if (!isImplicitArrayToStringCall(expression)) {
        return;
      }
      registerError(expression, expression, Boolean.FALSE);
    }

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (!isImplicitArrayToStringCall(expression)) {
        return;
      }
      registerError(expression, expression, Boolean.FALSE);
    }

    @Override
    public void visitArrayAccessExpression(@NotNull PsiArrayAccessExpression expression) {
      super.visitArrayAccessExpression(expression);
      if (!isImplicitArrayToStringCall(expression)) {
        return;
      }
      registerError(expression, expression, Boolean.FALSE);
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (isExplicitArrayToStringCall(expression)) {
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        registerMethodCallError(expression, qualifier, Boolean.TRUE);
        return;
      }
      if (!isImplicitArrayToStringCall(expression)) {
        return;
      }
      registerError(expression, expression, Boolean.FALSE);
    }

    private static boolean isExplicitArrayToStringCall(PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.TO_STRING.equals(methodName) || !expression.getArgumentList().isEmpty()) {
        return false;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return false;
      }
      final PsiType type = qualifier.getType();
      return type instanceof PsiArrayType;
    }

    private static boolean isImplicitArrayToStringCall(PsiExpression expression) {
      return ExpressionUtils.isImplicitToStringCall(expression) && expression.getType() instanceof PsiArrayType;
    }
  }
}