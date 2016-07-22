/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.ig.PsiReplacementUtil;
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
    new HashMap<>(8);

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
              PsiReplacementUtil.replaceExpression(methodCall, "true");
              return;
            }
            else if ("FALSE".equals(name)) {
              PsiReplacementUtil.replaceExpression(methodCall, "false");
              return;
            }
          }
        }
      }
      final String strippedQualifierText = strippedQualifier.getText();
      PsiReplacementUtil.replaceExpression(methodCall, strippedQualifierText);
    }
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryUnboxingVisitor();
  }

  private class UnnecessaryUnboxingVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!isUnboxingExpression(expression)) {
        return;
      }
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null || !canRemainBoxed(expression, qualifier)) {
        return;
      }
      registerError(expression);
    }

    private boolean canRemainBoxed(@NotNull PsiExpression expression, @NotNull PsiExpression unboxedExpression) {
      PsiElement parent = expression.getParent();
      while (parent instanceof PsiParenthesizedExpression) {
        expression = (PsiExpression)parent;
        parent = parent.getParent();
      }
      if (parent instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)parent;
        if (isPossibleObjectComparison(expression, polyadicExpression)) {
          return false;
        }
      }
      if (parent instanceof PsiTypeCastExpression) {
        final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)parent;
        final PsiTypeElement typeElement = typeCastExpression.getCastType();
        if (typeElement == null) {
          return false;
        }
        final PsiType castType = typeElement.getType();
        final PsiType expressionType = expression.getType();
        if (expressionType == null || !castType.isAssignableFrom(expressionType)) {
          return false;
        }
      }
      else if (parent instanceof PsiConditionalExpression) {
        final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)parent;
        final PsiExpression thenExpression = conditionalExpression.getThenExpression();
        if (thenExpression == null) {
          return false;
        }
        final PsiExpression elseExpression = conditionalExpression.getElseExpression();
        if (elseExpression == null) {
          return false;
        }
        if (PsiTreeUtil.isAncestor(thenExpression, expression, false)) {
          final PsiType type = elseExpression.getType();
          if (!(type instanceof PsiPrimitiveType)) {
            return false;
          }
        }
        else if (PsiTreeUtil.isAncestor(elseExpression, expression, false)) {
          final PsiType type = thenExpression.getType();
          if (!(type instanceof PsiPrimitiveType)) {
            return false;
          }
        }
      }
      else if (parent instanceof PsiExpressionList) {
        final PsiElement grandParent = parent.getParent();
        if (!(grandParent instanceof PsiCallExpression)) {
          return true;
        }
        final PsiCallExpression methodCallExpression = (PsiCallExpression)grandParent;
        if (!isSameMethodCalledWithoutUnboxing(methodCallExpression, expression, unboxedExpression)) {
          return false;
        }
      }
      if (onlyReportSuperfluouslyUnboxed) {
        final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, false, true);
        if (!(expectedType instanceof PsiClassType)) {
          return false;
        }
      }
      return true;
    }

    private boolean isPossibleObjectComparison(PsiExpression expression, PsiPolyadicExpression polyadicExpression) {
      if (!ComparisonUtils.isEqualityComparison(polyadicExpression)) {
        return false;
      }
      for (PsiExpression operand : polyadicExpression.getOperands()) {
        if (operand == expression) {
          continue;
        }
        if (!(operand.getType() instanceof PsiPrimitiveType) || isUnboxingExpression(operand)) {
          return true;
        }
      }
      return false;
    }

    private boolean isUnboxingExpression(PsiExpression expression) {
      if (!(expression instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
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
      final String unboxingMethod = s_unboxingMethods.get(qualifierTypeName);
      return unboxingMethod.equals(methodName);
    }

    private boolean isSameMethodCalledWithoutUnboxing(@NotNull PsiCallExpression callExpression,
                                                      @NotNull PsiExpression unboxingExpression,
                                                      @NotNull PsiExpression unboxedExpression) {
      final PsiMethod originalMethod = callExpression.resolveMethod();
      if (originalMethod == null) {
        return false;
      }
      final PsiMethod method = MethodCallUtils.findMethodWithReplacedArgument(callExpression, unboxingExpression, unboxedExpression);
      return originalMethod == method;
    }
  }
}