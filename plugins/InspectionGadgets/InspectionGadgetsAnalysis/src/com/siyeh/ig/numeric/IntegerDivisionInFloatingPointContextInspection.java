/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.numeric;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class IntegerDivisionInFloatingPointContextInspection extends BaseInspection {

  @NonNls
  static final Set<String> s_integralTypes = new HashSet<>(10);

  static {
    s_integralTypes.add("int");
    s_integralTypes.add("long");
    s_integralTypes.add("short");
    s_integralTypes.add("byte");
    s_integralTypes.add("char");
    s_integralTypes.add(CommonClassNames.JAVA_LANG_INTEGER);
    s_integralTypes.add(CommonClassNames.JAVA_LANG_LONG);
    s_integralTypes.add(CommonClassNames.JAVA_LANG_SHORT);
    s_integralTypes.add(CommonClassNames.JAVA_LANG_BYTE);
    s_integralTypes.add(CommonClassNames.JAVA_LANG_CHARACTER);
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "integer.division.in.floating.point.context.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "integer.division.in.floating.point.context.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IntegerDivisionInFloatingPointContextVisitor();
  }

  private static class IntegerDivisionInFloatingPointContextVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.DIV)) {
        return;
      }
      if (!hasIntegerDivision(expression)) {
        return;
      }
      final PsiExpression context = getContainingExpression(expression);
      if (context == null) {
        return;
      }
      final PsiType contextType;
      if (context instanceof PsiBinaryExpression) {
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)context;
        final PsiExpression lhs = binaryExpression.getLOperand();
        final PsiExpression rhs = binaryExpression.getROperand();
        if (rhs == null) {
          return;
        }
        if (PsiTreeUtil.isAncestor(lhs, expression, false)) {
          contextType = rhs.getType();
        }
        else if (PsiTreeUtil.isAncestor(binaryExpression.getROperand(), expression, false)) {
          contextType = lhs.getType();
        }
        else {
          return;
        }
      }
      else {
        contextType = ExpectedTypeUtils.findExpectedType(context, true);
      }
      if (!PsiType.FLOAT.equals(contextType) && !PsiType.DOUBLE.equals(contextType)) {
        return;
      }
      registerError(expression);
    }

    private static boolean hasIntegerDivision(@NotNull PsiPolyadicExpression expression) {
      final PsiExpression[] operands = expression.getOperands();
      return operands.length >= 2 && isIntegral(operands[0].getType()) && isIntegral(operands[1].getType());
    }

    private static boolean isIntegral(PsiType type) {
      return type != null && s_integralTypes.contains(type.getCanonicalText());
    }

    private static PsiExpression getContainingExpression(PsiExpression expression) {
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiBinaryExpression) {
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)parent;
        if (!ComparisonUtils.isComparisonOperation(binaryExpression.getOperationTokenType())) {
          return getContainingExpression(binaryExpression);
        }
        else {
          return (PsiExpression)parent;
        }
      }
      else if (parent instanceof PsiPolyadicExpression ||
               parent instanceof PsiParenthesizedExpression ||
               parent instanceof PsiPrefixExpression ||
               parent instanceof PsiConditionalExpression) {
        return getContainingExpression((PsiExpression)parent);
      }
      return expression;
    }
  }
}