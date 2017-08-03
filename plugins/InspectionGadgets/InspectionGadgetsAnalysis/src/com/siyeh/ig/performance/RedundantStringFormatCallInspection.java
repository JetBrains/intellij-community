/*
 * Copyright 2008-2017 Bas Leijdekkers
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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class RedundantStringFormatCallInspection extends BaseInspection {

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("redundant.string.format.call.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("redundant.string.format.call.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final Boolean printf = (Boolean)infos[0];
    return printf.booleanValue() ? new ReplaceWithPrintFix() : new RedundantStringFormatCallFix();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  private static class ReplaceWithPrintFix extends InspectionGadgetsFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("replace.printf.with.print.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      @NonNls final StringBuilder newExpression = new StringBuilder();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (qualifierExpression != null) {
        newExpression.append(qualifierExpression.getText()).append('.');
      }
      newExpression.append("print").append(methodCallExpression.getArgumentList().getText());
      PsiReplacementUtil.replaceExpression(methodCallExpression, newExpression.toString());
    }
  }

  private static class RedundantStringFormatCallFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("redundant.string.format.call.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement().getParent().getParent();
      if (!(element instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
      final PsiExpression[] arguments = methodCallExpression.getArgumentList().getExpressions();
      methodCallExpression.replace(arguments[arguments.length - 1]);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RedundantStringFormatCallVisitor();
  }

  private static class RedundantStringFormatCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      final boolean printf;
      if ("format".equals(methodName)) {
        printf = false;
      }
      else if ("printf".equals(methodName)) {
        printf = true;
      }
      else {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length > 2 || arguments.length == 0) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      final String className = aClass.getQualifiedName();
      if (printf) {
        if (!"java.io.PrintStream".equals(className)) {
          return;
        }
      }
      else if (!CommonClassNames.JAVA_LANG_STRING.equals(className)) {
        return;
      }
      final PsiExpression firstArgument = arguments[0];
      final PsiType firstType = firstArgument.getType();
      if (firstType == null) {
        return;
      }
      if (firstType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        if (arguments.length == 1 && !containsPercentN(firstArgument)) {
          registerMethodCallError(expression, Boolean.valueOf(printf));
        }
      }
      else if (firstType.equalsToText("java.util.Locale")) {
        if (arguments.length != 2) {
          return;
        }
        final PsiExpression secondArgument = arguments[1];
        final PsiType secondType = secondArgument.getType();
        if (secondType == null || !secondType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
          return;
        }
        if (containsPercentN(secondArgument)) {
          return;
        }
        registerMethodCallError(expression, Boolean.valueOf(printf));
      }
    }

    private static boolean containsPercentN(PsiExpression expression) {
      if (expression == null) {
        return false;
      }
      if (expression instanceof PsiLiteralExpression) {
        final PsiLiteralExpression literalExpression = (PsiLiteralExpression)expression;
        @NonNls final String expressionText = literalExpression.getText();
        return expressionText.contains("%n");
      }
      if (expression instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
        final IElementType tokenType = polyadicExpression.getOperationTokenType();
        if (!tokenType.equals(JavaTokenType.PLUS)) {
          return false;
        }
        final PsiExpression[] operands = polyadicExpression.getOperands();
        for (PsiExpression operand : operands) {
          if (containsPercentN(operand)) {
            return true;
          }
        }
      }
      return false;
    }
  }
}
