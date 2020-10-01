/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFieldImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.EqualityToEqualsFix;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NotNull;

import static com.siyeh.ig.psiutils.ComparisonUtils.isNullComparison;

public class NumberEqualityInspection extends BaseInspection {

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "number.comparison.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NumberEqualityVisitor();
  }

  @Override
  protected InspectionGadgetsFix @NotNull [] buildFixes(Object... infos) {
    return EqualityToEqualsFix.buildEqualityFixes((PsiBinaryExpression)infos[0]);
  }

  private static class NumberEqualityVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      if (!ComparisonUtils.isEqualityComparison(expression)) {
        return;
      }
      final PsiExpression rhs = expression.getROperand();
      if (!hasNumberType(rhs)) {
        return;
      }
      final PsiExpression lhs = expression.getLOperand();
      if (!hasNumberType(lhs)) {
        return;
      }
      if (isUniqueConstant(rhs) || isUniqueConstant(lhs)) return;
      if (isOneOfVariableDefinitivelyNull(expression, lhs, rhs)) return;
      registerError(expression.getOperationSign(), expression);
    }

    private static boolean isOneOfVariableDefinitivelyNull(final PsiExpression expression,
                                                           final PsiExpression lhs, final PsiExpression rhs) {
      final PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
      final PsiConditionalExpression ternary = ObjectUtils.tryCast(parent, PsiConditionalExpression.class);

      if (ternary != null) {
        final PsiExpression condition = PsiUtil.skipParenthesizedExprDown(ternary.getCondition());
        final PsiExpression thenExpression = PsiUtil.skipParenthesizedExprDown(ternary.getThenExpression());
        final PsiExpression elseExpression = PsiUtil.skipParenthesizedExprDown(ternary.getElseExpression());
        return isOneOfVariableDefinitivelyNull(expression, condition, thenExpression, elseExpression, lhs, rhs);
      } else {
        final PsiElement grandParent = parent.getParent();
        PsiIfStatement ifStatement = ObjectUtils.tryCast(grandParent, PsiIfStatement.class);
        PsiElement conditionalBranchContainsNumberEquality = expression.getParent();

        if (ifStatement == null) {
          if (isElementInIfStmtWithCurlyBraces(grandParent)) {
            ifStatement = ObjectUtils.tryCast(grandParent.getParent().getParent(), PsiIfStatement.class);
            conditionalBranchContainsNumberEquality = conditionalBranchContainsNumberEquality.getParent().getParent();
          }
        }

        if (ifStatement != null) {
          final PsiExpression condition = PsiUtil.skipParenthesizedExprDown(ifStatement.getCondition());
          final PsiStatement thenBranch = ifStatement.getThenBranch();
          final PsiStatement elseBranch = ifStatement.getElseBranch();
          return isOneOfVariableDefinitivelyNull(conditionalBranchContainsNumberEquality, condition, thenBranch, elseBranch, lhs, rhs);
        }
      }
      return false;
    }

    private static boolean isElementInIfStmtWithCurlyBraces(PsiElement element) {
      final PsiCodeBlock codeBlock = ObjectUtils.tryCast(element, PsiCodeBlock.class);
      if (codeBlock == null) return false;

      final PsiBlockStatement blockStatement = ObjectUtils.tryCast(codeBlock.getParent(), PsiBlockStatement.class);
      if (blockStatement == null) return false;

      final PsiIfStatement ifStatement = ObjectUtils.tryCast(blockStatement.getParent(), PsiIfStatement.class);
      return ifStatement != null;
    }

    private static boolean isOneOfVariableDefinitivelyNull(PsiElement conditionalBranchContainsNumberEquality,
                                                           PsiExpression condition, PsiElement thenElement, PsiElement elseElement,
                                                           PsiExpression lhsExpr, PsiExpression rhsExpr) {
      if (condition == null) return false;
      boolean isNegated = false;
      PsiBinaryExpression binOp = ObjectUtils.tryCast(condition, PsiBinaryExpression.class);
      if (binOp == null && BoolUtils.isNegation(condition)) {
        PsiExpression operand = PsiUtil.skipParenthesizedExprDown(((PsiPrefixExpression) condition).getOperand());
        binOp = ObjectUtils.tryCast(operand, PsiBinaryExpression.class);
        isNegated = true;
      }

      if (binOp == null) return false;

      final IElementType tokenType = binOp.getOperationTokenType();
      final PsiVariable lhsVar = (PsiVariable)((PsiReferenceExpression)lhsExpr).resolve();
      final PsiVariable rhsVar = (PsiVariable)((PsiReferenceExpression)rhsExpr).resolve();
      if (lhsVar == null || rhsVar == null) return false;

      final PsiExpression lOperand = binOp.getLOperand();
      final PsiExpression rOperand = binOp.getROperand();
      final boolean isTwoNotNullComparison = isTwoNullComparison(lOperand, rOperand, lhsVar, rhsVar, false);
      final boolean isTwoNullComparison = isTwoNullComparison(lOperand, rOperand, lhsVar, rhsVar, true);
      final boolean isAnd = tokenType == JavaTokenType.ANDAND || tokenType == JavaTokenType.AND;
      final boolean isOr = tokenType == JavaTokenType.OROR || tokenType == JavaTokenType.OR;

      return (conditionalBranchContainsNumberEquality == (isNegated ? thenElement : elseElement) && isAnd && isTwoNotNullComparison)
             || (conditionalBranchContainsNumberEquality == (isNegated ? elseElement : thenElement) && isOr && isTwoNullComparison);
    }

    private static boolean isTwoNullComparison(PsiExpression lOperand, PsiExpression rOperand,
                                               PsiVariable lhsVariable, PsiVariable rhsVariable,
                                               boolean equal) {
      return (isNullComparison(lOperand, lhsVariable, equal) && isNullComparison(rOperand, rhsVariable, equal)) ||
             (isNullComparison(rOperand, lhsVariable, equal) && isNullComparison(lOperand, rhsVariable, equal));
    }

    private static boolean isUniqueConstant(PsiExpression expression) {
      PsiReferenceExpression ref = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(expression), PsiReferenceExpression.class);
      if (ref == null) return false;
      PsiField target = ObjectUtils.tryCast(ref.resolve(), PsiField.class);
      if (target == null) return false;
      if (target instanceof PsiEnumConstant) return true;
      if (!(target instanceof PsiFieldImpl)) return false;
      if (!target.hasModifierProperty(PsiModifier.STATIC) || !target.hasModifierProperty(PsiModifier.FINAL)) return false;
      PsiExpression initializer = PsiFieldImpl.getDetachedInitializer(target);
      return ExpressionUtils.isNewObject(initializer);
    }

    private static boolean hasNumberType(PsiExpression expression) {
      return TypeUtils.expressionHasTypeOrSubtype(expression, CommonClassNames.JAVA_LANG_NUMBER);
    }
  }
}