/*
 * Copyright 2010-2018 Bas Leijdekkers
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
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StringConcatenationInMessageFormatCallInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("string.concatenation.in.message.format.call.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("string.concatenation.in.message.format.call.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)infos[0];
    final String referenceName = referenceExpression.getReferenceName();
    return new StringConcatenationInFormatCallFix(referenceName);
  }

  private static class StringConcatenationInFormatCallFix extends InspectionGadgetsFix {

    private final String variableName;

    public StringConcatenationInFormatCallFix(String variableName) {
      this.variableName = variableName;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("string.concatenation.in.format.call.quickfix", variableName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace concatenation with argument";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiBinaryExpression)) {
        return;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)element;
      final PsiElement parent = binaryExpression.getParent();
      if (!(parent instanceof PsiExpressionList)) {
        return;
      }
      final PsiExpressionList expressionList = (PsiExpressionList)parent;
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiExpression rhs = binaryExpression.getROperand();
      if (rhs == null) {
        return;
      }
      final PsiExpression[] expressions = expressionList.getExpressions();
      final int parameter = expressions.length - 1;
      expressionList.add(rhs);
      final Object constant =
        ExpressionUtils.computeConstantExpression(lhs);
      if (constant instanceof String) {
        final PsiExpression newExpression = addParameter(lhs, parameter);
        if (newExpression == null) {
          expressionList.addAfter(lhs, binaryExpression);
        }
        else {
          expressionList.addAfter(newExpression, binaryExpression);
        }
      }
      else {
        expressionList.addAfter(lhs, binaryExpression);
      }
      binaryExpression.delete();
    }

    @Nullable
    private static PsiExpression addParameter(PsiExpression expression, int parameterNumber) {
      if (expression instanceof PsiBinaryExpression) {
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
        final PsiExpression rhs = binaryExpression.getROperand();
        if (rhs == null) {
          return null;
        }
        final PsiExpression newExpression = addParameter(rhs, parameterNumber);
        if (newExpression == null) {
          return null;
        }
        rhs.replace(newExpression);
        return expression;
      }
      else if (expression instanceof PsiLiteralExpression) {
        final PsiLiteralExpression literalExpression = (PsiLiteralExpression)expression;
        final Object value = literalExpression.getValue();
        if (!(value instanceof String)) {
          return null;
        }
        final Project project = expression.getProject();
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        return factory.createExpressionFromText("\"" + value + '{' + parameterNumber + "}\"", null);
      }
      else {
        return null;
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationInMessageFormatCallVisitor();
  }

  private static class StringConcatenationInMessageFormatCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!isMessageFormatCall(expression)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      final PsiExpression firstArgument = arguments[0];
      final PsiType type = firstArgument.getType();
      if (type == null) {
        return;
      }
      final int formatArgumentIndex;
      if ("java.util.Locale".equals(type.getCanonicalText()) && arguments.length > 1) {
        formatArgumentIndex = 1;
      }
      else {
        formatArgumentIndex = 0;
      }
      final PsiExpression formatArgument = arguments[formatArgumentIndex];
      final PsiType formatArgumentType = formatArgument.getType();
      if (formatArgumentType == null || !formatArgumentType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return;
      }
      if (!(formatArgument instanceof PsiBinaryExpression)) {
        return;
      }
      if (PsiUtil.isConstantExpression(formatArgument)) {
        return;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)formatArgument;
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiType lhsType = lhs.getType();
      if (lhsType == null || !lhsType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return;
      }
      final PsiExpression rhs = binaryExpression.getROperand();
      if (!(rhs instanceof PsiReferenceExpression)) {
        return;
      }
      registerError(formatArgument, rhs);
    }

    private static boolean isMessageFormatCall(PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String referenceName = methodExpression.getReferenceName();
      if (!"format".equals(referenceName)) {
        return false;
      }
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (!(qualifierExpression instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifierExpression;
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiClass)) {
        return false;
      }
      final PsiClass aClass = (PsiClass)target;
      return InheritanceUtil.isInheritor(aClass, "java.text.MessageFormat");
    }
  }
}
