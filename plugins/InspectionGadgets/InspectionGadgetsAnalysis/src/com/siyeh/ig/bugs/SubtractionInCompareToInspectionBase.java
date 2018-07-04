/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.DfaFactType;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodMatcher;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class SubtractionInCompareToInspectionBase extends BaseInspection {

  protected final MethodMatcher methodMatcher;

  public SubtractionInCompareToInspectionBase() {
    methodMatcher = new MethodMatcher()
      .add(CommonClassNames.JAVA_UTIL_COLLECTION, "size")
      .add(CommonClassNames.JAVA_UTIL_MAP, "size")
      .add(CommonClassNames.JAVA_LANG_STRING, "length")
      .add(CommonClassNames.JAVA_LANG_ABSTRACT_STRING_BUILDER, "length")
      .finishDefault();
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    super.readSettings(node);
    methodMatcher.readSettings(node);
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    super.writeSettings(node);
    methodMatcher.writeSettings(node);
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("subtraction.in.compareto.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("subtraction.in.compareto.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SubtractionInCompareToVisitor();
  }

  private class SubtractionInCompareToVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.MINUS) || isSafeSubtraction(expression)) {
        return;
      }
      final PsiLambdaExpression lambdaExpression =
        PsiTreeUtil.getParentOfType(expression, PsiLambdaExpression.class, true, PsiMember.class);
      if (lambdaExpression != null) {
        final PsiClass functionalInterface = PsiUtil.resolveClassInType(lambdaExpression.getFunctionalInterfaceType());
        if (functionalInterface != null && CommonClassNames.JAVA_UTIL_COMPARATOR.equals(functionalInterface.getQualifiedName())) {
          registerError(expression);
          return;
        }
      }
      final PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
      if (!MethodUtils.isCompareTo(method) && !MethodUtils.isComparatorCompare(method)) {
        return;
      }
      registerError(expression);
    }

    private boolean isSafeSubtraction(PsiBinaryExpression binaryExpression) {
      final PsiType type = binaryExpression.getType();
      if (PsiType.FLOAT.equals(type) || PsiType.DOUBLE.equals(type)) {
        // Difference of floats and doubles never overflows.
        // It may lose a precision, but it's not the case when we compare the result with zero
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(binaryExpression.getParent());
        if(parent instanceof PsiTypeCastExpression) {
          PsiType castType = ((PsiTypeCastExpression)parent).getType();
          if(PsiType.INT.equals(castType) || PsiType.LONG.equals(castType)) {
            // Precision is lost if result is cast to int/long (e.g. (int)(1.0 - 0.5) == 0)
            return false;
          }
        }
        return true;
      }
      if (ExpressionUtils.isEvaluatedAtCompileTime(binaryExpression)) {
        // If compile time expression overflows, we have separate NumericOverflowInspection for this
        return true;
      }
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiExpression rhs = binaryExpression.getROperand();
      if (rhs == null) return true;
      final PsiType lhsType = lhs.getType();
      final PsiType rhsType = rhs.getType();
      if (lhsType == null || rhsType == null) {
        return false;
      }
      if ((PsiType.BYTE.equals(lhsType) || PsiType.SHORT.equals(lhsType) || PsiType.CHAR.equals(lhsType)) &&
          (PsiType.BYTE.equals(rhsType) || PsiType.SHORT.equals(rhsType) || PsiType.CHAR.equals(rhsType))) {
        return true;
      }
      if (isSafeOperand(lhs) && isSafeOperand(rhs)) return true;
      LongRangeSet leftRange = CommonDataflow.getExpressionFact(lhs, DfaFactType.RANGE);
      LongRangeSet rightRange = CommonDataflow.getExpressionFact(rhs, DfaFactType.RANGE);
      if (leftRange != null && !leftRange.isEmpty() && rightRange != null && !rightRange.isEmpty()) {
        long leftMin = leftRange.min();
        long leftMax = leftRange.max();
        long rightMin = rightRange.min();
        long rightMax = rightRange.max();
        if (PsiType.INT.equals(type) && !overflowsInt(leftMin, rightMax) && !overflowsInt(leftMax, rightMin)) {
          return true;
        }
        if (PsiType.LONG.equals(type) && !overflowsLong(leftMin, rightMax) && !overflowsLong(leftMax, rightMin)) {
          return true;
        }
      }
      return false;
    }

    private boolean overflowsInt(long a, long b) {
      long diff = a - b;
      return diff < Integer.MIN_VALUE || diff > Integer.MAX_VALUE;
    }

    private boolean overflowsLong(long a, long b) {
      long diff = a - b;
      // Hacker's Delight 2nd Edition, 2-13 Overflow Detection
      return ((a ^ b) & (a ^ diff)) < 0;
    }

    private boolean isSafeOperand(PsiExpression operand) {
      operand = ParenthesesUtils.stripParentheses(operand);
      if (operand instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)operand;
        return methodMatcher.matches(methodCallExpression);
      }
      return ExpressionUtils.getArrayFromLengthExpression(operand) != null;
    }
  }
}