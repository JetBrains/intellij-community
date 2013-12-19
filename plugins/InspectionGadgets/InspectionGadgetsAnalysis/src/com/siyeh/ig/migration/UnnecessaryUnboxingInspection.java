/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.migration;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public class UnnecessaryUnboxingInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean onlyReportSuperfluouslyUnboxed = false;

  @NonNls static final Map<String, String> s_unboxingMethods =
    new HashMap<String, String>(8);

  static {
    s_unboxingMethods.put(CommonClassNames.JAVA_LANG_INTEGER, "intValue");
    s_unboxingMethods.put(CommonClassNames.JAVA_LANG_SHORT, "shortValue");
    s_unboxingMethods.put(CommonClassNames.JAVA_LANG_BOOLEAN, "booleanValue");
    s_unboxingMethods.put(CommonClassNames.JAVA_LANG_LONG, "longValue");
    s_unboxingMethods.put(CommonClassNames.JAVA_LANG_BYTE, "byteValue");
    s_unboxingMethods.put(CommonClassNames.JAVA_LANG_FLOAT, "floatValue");
    s_unboxingMethods.put(CommonClassNames.JAVA_LANG_DOUBLE, "doubleValue");
    s_unboxingMethods.put(CommonClassNames.JAVA_LANG_CHARACTER, "charValue");
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "unnecessary.unboxing.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "unnecessary.unboxing.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("unnecessary.unboxing.superfluous.option"),
                                          this, "onlyReportSuperfluouslyUnboxed");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryUnboxingVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryUnboxingFix();
  }

  private static class UnnecessaryUnboxingFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "unnecessary.unboxing.remove.quickfix");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)descriptor.getPsiElement();
      final PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      final PsiExpression strippedQualifier = ParenthesesUtils.stripParentheses(qualifier);
      if (strippedQualifier == null) {
        return;
      }
      if (strippedQualifier instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)strippedQualifier;
        final PsiElement element = referenceExpression.resolve();
        if (element instanceof PsiField) {
          final PsiField field = (PsiField)element;
          final PsiClass containingClass = field.getContainingClass();
          if (containingClass == null) {
            return;
          }
          final String classname = containingClass.getQualifiedName();
          if (CommonClassNames.JAVA_LANG_BOOLEAN.equals(classname)) {
            @NonNls final String name = field.getName();
            if ("TRUE".equals(name)) {
              replaceExpression(methodCall, "true");
              return;
            }
            else if ("FALSE".equals(name)) {
              replaceExpression(methodCall, "false");
              return;
            }
          }
        }
      }
      final String strippedQualifierText = strippedQualifier.getText();
      replaceExpression(methodCall, strippedQualifierText);
    }
  }

  private class UnnecessaryUnboxingVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!PsiUtil.isLanguageLevel5OrHigher(expression)) {
        return;
      }
      if (!isUnboxingExpression(expression)) {
        return;
      }
      final PsiExpression containingExpression = getContainingExpression(expression);
      if (isPossibleObjectComparison(expression, containingExpression)) {
        return;
      }
      if (containingExpression instanceof PsiTypeCastExpression) {
        final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)containingExpression;
        final PsiTypeElement typeElement = typeCastExpression.getCastType();
        if (typeElement == null) {
          return;
        }
        final PsiType castType = typeElement.getType();
        final PsiType expressionType = expression.getType();
        if (expressionType == null || !castType.isAssignableFrom(expressionType)) {
          return;
        }
      }
      else if (containingExpression instanceof PsiConditionalExpression) {
        final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)containingExpression;
        final PsiExpression thenExpression = conditionalExpression.getThenExpression();
        if (thenExpression == null) {
          return;
        }
        final PsiExpression elseExpression = conditionalExpression.getElseExpression();
        if (elseExpression == null) {
          return;
        }
        if (PsiTreeUtil.isAncestor(thenExpression, expression, false)) {
          final PsiType type = elseExpression.getType();
          if (!(type instanceof PsiPrimitiveType)) {
            return;
          }
        }
        else if (PsiTreeUtil.isAncestor(elseExpression, expression, false)) {
          final PsiType type = thenExpression.getType();
          if (!(type instanceof PsiPrimitiveType)) {
            return;
          }
        }
      }
      else if (containingExpression instanceof PsiCallExpression) {
        final PsiCallExpression methodCallExpression = (PsiCallExpression)containingExpression;
        if (!isSameMethodCalledWithoutUnboxing(methodCallExpression, expression)) {
          return;
        }
      }
      if (onlyReportSuperfluouslyUnboxed) {
        final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, false, true);
        if (!(expectedType instanceof PsiClassType)) {
          return;
        }
      }
      registerError(expression);
    }

    private boolean isPossibleObjectComparison(PsiMethodCallExpression expression, PsiExpression containingExpression) {
      if (!(containingExpression instanceof PsiBinaryExpression)) {
        return false;
      }
      final PsiBinaryExpression binaryExpression =
        (PsiBinaryExpression)containingExpression;
      if (!ComparisonUtils.isEqualityComparison(binaryExpression)) {
        return false;
      }
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiExpression rhs = binaryExpression.getROperand();
      if (rhs == null) {
        return true;
      }
      if (expression == lhs) {
        if (!(rhs.getType() instanceof PsiPrimitiveType) ||
            isUnboxingExpression(rhs)) {
          return true;
        }
      }
      if (expression == rhs) {
        if (!(lhs.getType() instanceof PsiPrimitiveType) ||
            isUnboxingExpression(lhs)) {
          return true;
        }
      }
      return false;
    }

    private boolean isUnboxingExpression(PsiExpression expression) {
      if (!(expression instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)expression;
      final PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return false;
      }
      final PsiType qualifierType = qualifier.getType();
      if (qualifierType == null) {
        return false;
      }
      final String qualifierTypeName = qualifierType.getCanonicalText();
      if (!s_unboxingMethods.containsKey(qualifierTypeName)) {
        return false;
      }
      final String methodName = methodExpression.getReferenceName();
      final String unboxingMethod =
        s_unboxingMethods.get(qualifierTypeName);
      return unboxingMethod.equals(methodName);
    }

    private boolean isSameMethodCalledWithoutUnboxing(
      @NotNull PsiCallExpression callExpression,
      @NotNull PsiMethodCallExpression unboxingExpression) {
      final PsiExpressionList argumentList =
        callExpression.getArgumentList();
      if (argumentList == null) {
        return false;
      }
      final PsiExpression[] expressions = argumentList.getExpressions();
      final PsiMethod originalMethod =
        callExpression.resolveMethod();
      if (originalMethod == null) {
        return false;
      }
      final String name = originalMethod.getName();
      final PsiClass containingClass =
        originalMethod.getContainingClass();
      if (containingClass == null) {
        return false;
      }
      final PsiType[] types = new PsiType[expressions.length];
      for (int i = 0; i < expressions.length; i++) {
        final PsiExpression expression = expressions[i];
        final PsiType type = expression.getType();
        if (unboxingExpression.equals(expression)) {
          if (!(type instanceof PsiPrimitiveType)) {
            return false;
          }
          final PsiPrimitiveType primitiveType =
            (PsiPrimitiveType)type;
          types[i] = primitiveType.getBoxedType(unboxingExpression);
        }
        else {
          types[i] = type;
        }
      }
      final PsiMethod[] methods =
        containingClass.findMethodsByName(name, true);
      for (final PsiMethod method : methods) {
        if (!originalMethod.equals(method)) {
          if (MethodCallUtils.isApplicable(method,
                                           PsiSubstitutor.EMPTY, types)) {
            return false;
          }
        }
      }
      return true;
    }

    @Nullable
    private PsiExpression getContainingExpression(@NotNull PsiElement expression) {
      final PsiElement parent = expression.getParent();
      if (parent == null || !(parent instanceof PsiExpression) &&
                            !(parent instanceof PsiExpressionList)) {
        return null;
      }
      if (parent instanceof PsiParenthesizedExpression ||
          parent instanceof PsiExpressionList) {
        return getContainingExpression(parent);
      }
      else {
        return (PsiExpression)parent;
      }
    }
  }
}