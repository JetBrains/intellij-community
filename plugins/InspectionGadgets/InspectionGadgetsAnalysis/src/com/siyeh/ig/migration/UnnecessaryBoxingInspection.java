/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public class UnnecessaryBoxingInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean onlyReportSuperfluouslyBoxed = false;

  @NonNls static final Map<String, String> boxedPrimitiveMap = new HashMap<String, String>(8);

  static {
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_INTEGER, "int");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_SHORT, "short");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_BOOLEAN, "boolean");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_LONG, "long");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_BYTE, "byte");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_FLOAT, "float");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_DOUBLE, "double");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_CHARACTER, "char");
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unnecessary.boxing.display.name");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("unnecessary.boxing.superfluous.option"),
                                          this, "onlyReportSuperfluouslyBoxed");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unnecessary.boxing.problem.descriptor");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryBoxingFix();
  }

  private static class UnnecessaryBoxingFix extends InspectionGadgetsFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix");
    }

    @Override
    public void doFix(@NotNull Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiCallExpression expression = (PsiCallExpression)descriptor.getPsiElement();
      final PsiType boxedType = expression.getType();
      if (boxedType == null) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression unboxedExpression = arguments[0];
      final PsiType unboxedType = unboxedExpression.getType();
      if (unboxedType == null) {
        return;
      }
      final String cast = getCastString(unboxedType, boxedType);
      if (cast == null) {
        return;
      }
      final int precedence = ParenthesesUtils.getPrecedence(unboxedExpression);
      if (!cast.isEmpty() && precedence > ParenthesesUtils.TYPE_CAST_PRECEDENCE) {
        PsiReplacementUtil.replaceExpression(expression, cast + '(' + unboxedExpression.getText() + ')');
      }
      else {
        PsiReplacementUtil.replaceExpression(expression, cast + unboxedExpression.getText());
      }
    }

    @Nullable
    private static String getCastString(@NotNull PsiType fromType, @NotNull PsiType toType) {
      final String toTypeText = toType.getCanonicalText();
      final String fromTypeText = fromType.getCanonicalText();
      final String unboxedType = boxedPrimitiveMap.get(toTypeText);
      if (unboxedType == null) {
        return null;
      }
      if (fromTypeText.equals(unboxedType)) {
        return "";
      }
      else {
        return '(' + unboxedType + ')';
      }
    }
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryBoxingVisitor();
  }

  private class UnnecessaryBoxingVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiType constructorType = expression.getType();
      if (constructorType == null) {
        return;
      }
      final String constructorTypeText = constructorType.getCanonicalText();
      if (!boxedPrimitiveMap.containsKey(constructorTypeText)) {
        return;
      }
      final PsiMethod constructor = expression.resolveConstructor();
      if (constructor == null) {
        return;
      }
      final PsiParameterList parameterList = constructor.getParameterList();
      if (parameterList.getParametersCount() != 1) {
        return;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiParameter parameter = parameters[0];
      final PsiType parameterType = parameter.getType();
      final String parameterTypeText = parameterType.getCanonicalText();
      final String boxableConstructorType = boxedPrimitiveMap.get(constructorTypeText);
      if (!boxableConstructorType.equals(parameterTypeText)) {
        return;
      }
      if (!canBeUnboxed(expression)) {
        return;
      }
      if (onlyReportSuperfluouslyBoxed) {
        final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, false, true);
        if (!(expectedType instanceof PsiPrimitiveType)) {
          return;
        }
      }
      registerError(expression);
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      if (!(arguments[0].getType() instanceof PsiPrimitiveType)) {
        return;
      }
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls
      final String referenceName = methodExpression.getReferenceName();
      if (!"valueOf".equals(referenceName)) {
        return;
      }
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (!(qualifierExpression instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifierExpression;
      final String canonicalText = referenceExpression.getCanonicalText();
      if (!boxedPrimitiveMap.containsKey(canonicalText)) {
        return;
      }
      if (!canBeUnboxed(expression)) {
        return;
      }
      registerError(expression);
    }

    private boolean canBeUnboxed(PsiExpression expression) {
      PsiElement parent = expression.getParent();
      while (parent instanceof PsiParenthesizedExpression) {
        parent = parent.getParent();
      }
      if (parent instanceof PsiExpressionStatement || parent instanceof PsiReferenceExpression) {
        return false;
      }
      else if (parent instanceof PsiTypeCastExpression) {
        final PsiTypeCastExpression castExpression = (PsiTypeCastExpression)parent;
        final PsiType castType = castExpression.getType();
        if (castType instanceof PsiClassType) {
          final PsiClassType classType = (PsiClassType)castType;
          final PsiClass aClass = classType.resolve();
          if (aClass instanceof PsiTypeParameter) {
            return false;
          }
        }
      }
      else if (parent instanceof PsiConditionalExpression) {
        final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)parent;
        final PsiExpression thenExpression = conditionalExpression.getThenExpression();
        final PsiExpression elseExpression = conditionalExpression.getElseExpression();
        if (elseExpression == null || thenExpression == null) {
          return false;
        }
        if (PsiTreeUtil.isAncestor(thenExpression, expression, false)) {
          final PsiType type = elseExpression.getType();
          return type instanceof PsiPrimitiveType;
        }
        else if (PsiTreeUtil.isAncestor(elseExpression, expression, false)) {
          final PsiType type = thenExpression.getType();
          return type instanceof PsiPrimitiveType;
        }
        else {
          return true;
        }
      }
      else if (parent instanceof PsiBinaryExpression) {
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)parent;
        final PsiExpression lhs = binaryExpression.getLOperand();
        final PsiExpression rhs = binaryExpression.getROperand();
        if (rhs == null) {
          return false;
        }
        final PsiType rhsType = rhs.getType();
        if (rhsType == null) {
          return false;
        }
        final PsiType lhsType = lhs.getType();
        if (lhsType == null) {
          return false;
        }
        if (PsiTreeUtil.isAncestor(rhs, expression, false)) {
          final PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(rhsType);
          return unboxedType != null && unboxedType.isAssignableFrom(lhsType);
        }
        else {
          final PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(lhsType);
          return unboxedType != null && unboxedType.isAssignableFrom(rhsType);
        }
      }
      final PsiCallExpression containingMethodCallExpression = getParentMethodCallExpression(expression);
      return containingMethodCallExpression == null || isSameMethodCalledWithoutBoxing(containingMethodCallExpression, expression);
    }

    @Nullable
    private PsiCallExpression getParentMethodCallExpression(@NotNull PsiElement expression) {
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiParenthesizedExpression || parent instanceof PsiExpressionList) {
        return getParentMethodCallExpression(parent);
      }
      else if (parent instanceof PsiCallExpression) {
        return (PsiCallExpression)parent;
      }
      else {
        return null;
      }
    }

    private boolean isSameMethodCalledWithoutBoxing(@NotNull PsiCallExpression methodCallExpression,
                                                    @NotNull PsiExpression boxingExpression) {
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      if (argumentList == null) {
        return false;
      }
      final PsiExpression[] expressions = argumentList.getExpressions();
      final PsiMethod originalMethod = methodCallExpression.resolveMethod();
      if (originalMethod == null) {
        return false;
      }
      final String name = originalMethod.getName();
      final PsiClass containingClass = originalMethod.getContainingClass();
      if (containingClass == null) {
        return false;
      }
      final PsiType[] types = PsiType.createArray(expressions.length);

      for (int i = 0; i < expressions.length; i++) {
        final PsiExpression expression = expressions[i];
        final PsiType type = expression.getType();
        if (boxingExpression.equals(expression)) {
          final PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(type);
          if (unboxedType == null) {
            return false;
          }
          types[i] = unboxedType;
        }
        else {
          types[i] = type;
        }
      }
      final PsiMethod[] methods = containingClass.findMethodsByName(name, true);
      for (final PsiMethod method : methods) {
        if (!originalMethod.equals(method)) {
          if (MethodCallUtils.isApplicable(method, PsiSubstitutor.EMPTY, types)) {
            return false;
          }
        }
      }
      return true;
    }
  }
}