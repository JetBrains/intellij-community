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
package com.siyeh.ipp.trivialif;

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceIfWithConditionalIntention extends Intention {

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ReplaceIfWithConditionalPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final PsiIfStatement ifStatement = (PsiIfStatement)element.getParent();
    if (ifStatement == null) {
      return;
    }
    if (ReplaceIfWithConditionalPredicate.isReplaceableAssignment(ifStatement)) {
      final PsiExpression condition = ifStatement.getCondition();
      if (condition == null) {
        return;
      }
      final PsiStatement thenBranch = ifStatement.getThenBranch();
      final PsiExpressionStatement strippedThenBranch = (PsiExpressionStatement)ControlFlowUtils.stripBraces(thenBranch);
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      final PsiExpressionStatement strippedElseBranch = (PsiExpressionStatement)ControlFlowUtils.stripBraces(elseBranch);
      final PsiAssignmentExpression thenAssign = (PsiAssignmentExpression)strippedThenBranch.getExpression();
      final PsiAssignmentExpression elseAssign = (PsiAssignmentExpression)strippedElseBranch.getExpression();
      CommentTracker commentTracker = new CommentTracker();
      final PsiExpression lhs = thenAssign.getLExpression();
      final String lhsText = commentTracker.text(lhs);
      final PsiJavaToken sign = thenAssign.getOperationSign();
      final String operator = sign.getText();
      final PsiExpression thenRhs = thenAssign.getRExpression();
      if (thenRhs == null) {
        return;
      }
      final PsiExpression elseRhs = elseAssign.getRExpression();
      if (elseRhs == null) {
        return;
      }
      final String conditional = getConditionalText(condition, thenRhs, elseRhs, thenAssign.getType(), commentTracker);
      PsiReplacementUtil.replaceStatement(ifStatement, lhsText + operator + conditional + ';', commentTracker);
    }
    else if (ReplaceIfWithConditionalPredicate.isReplaceableReturn(ifStatement)) {
      final PsiExpression condition = ifStatement.getCondition();
      if (condition == null) {
        return;
      }
      final PsiStatement thenBranch = ifStatement.getThenBranch();
      final PsiReturnStatement thenReturn = (PsiReturnStatement)ControlFlowUtils.stripBraces(thenBranch);
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      final PsiReturnStatement elseReturn = (PsiReturnStatement)ControlFlowUtils.stripBraces(elseBranch);
      final PsiExpression thenReturnValue = thenReturn.getReturnValue();
      if (thenReturnValue == null) {
        return;
      }
      final PsiExpression elseReturnValue = elseReturn.getReturnValue();
      if (elseReturnValue == null) {
        return;
      }
      CommentTracker commentTracker = new CommentTracker();
      final String conditional = getConditional(condition, thenReturn, thenReturnValue, elseReturnValue, commentTracker);
      if (conditional == null) {
        return;
      }
      PsiReplacementUtil.replaceStatement(ifStatement, "return " + conditional + ';', commentTracker);
    }
    else if (ReplaceIfWithConditionalPredicate.isReplaceableMethodCall(ifStatement)) {
      final PsiExpression condition = ifStatement.getCondition();
      if (condition == null) {
        return;
      }
      CommentTracker commentTracker = new CommentTracker();
      final PsiExpressionStatement thenBranch = (PsiExpressionStatement)ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
      final PsiExpressionStatement elseBranch = (PsiExpressionStatement)ControlFlowUtils.stripBraces(ifStatement.getElseBranch());
      final PsiMethodCallExpression thenMethodCallExpression = (PsiMethodCallExpression)thenBranch.getExpression();
      final PsiMethodCallExpression elseMethodCallExpression = (PsiMethodCallExpression)elseBranch.getExpression();
      final StringBuilder replacementText = new StringBuilder(commentTracker.text(thenMethodCallExpression.getMethodExpression()));
      replacementText.append('(');
      final PsiExpressionList thenArgumentList = thenMethodCallExpression.getArgumentList();
      final PsiExpression[] thenArguments = thenArgumentList.getExpressions();
      final PsiExpressionList elseArgumentList = elseMethodCallExpression.getArgumentList();
      final PsiExpression[] elseArguments = elseArgumentList.getExpressions();
      for (int i = 0, length = thenArguments.length; i < length; i++) {
        if (i > 0) {
          replacementText.append(',');
        }
        final PsiExpression thenArgument = thenArguments[i];
        final PsiExpression elseArgument = elseArguments[i];
        if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(thenArgument, elseArgument)) {
          replacementText.append(commentTracker.text(thenArgument));
        }
        else {
          final PsiMethod method = thenMethodCallExpression.resolveMethod();
          if (method == null) {
            return;
          }
          final PsiParameterList parameterList = method.getParameterList();
          final PsiType requiredType = parameterList.getParameters()[i].getType();
          final String conditionalText = getConditionalText(condition, thenArgument, elseArgument, requiredType, commentTracker);
          if (conditionalText == null) {
            return;
          }
          replacementText.append(conditionalText);
        }
      }
      replacementText.append(");");
      PsiReplacementUtil.replaceStatement(ifStatement, replacementText.toString(), commentTracker);
    }
    else if (ReplaceIfWithConditionalPredicate.isReplaceableImplicitReturn(ifStatement)) {
      final PsiExpression condition = ifStatement.getCondition();
      if (condition == null) {
        return;
      }
      final PsiReturnStatement thenBranch = (PsiReturnStatement)ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
      final PsiExpression thenReturnValue = thenBranch.getReturnValue();
      if (thenReturnValue == null) {
        return;
      }
      final PsiReturnStatement elseBranch = PsiTreeUtil.getNextSiblingOfType(ifStatement, PsiReturnStatement.class);
      if (elseBranch == null) {
        return;
      }
      final PsiExpression elseReturnValue = elseBranch.getReturnValue();
      if (elseReturnValue == null) {
        return;
      }
      CommentTracker commentTracker = new CommentTracker();
      final String conditional = getConditional(condition, thenBranch, thenReturnValue, elseReturnValue, commentTracker);
      if (conditional == null) return;
      commentTracker.delete(elseBranch);
      PsiReplacementUtil.replaceStatement(ifStatement, "return " + conditional + ';', commentTracker);
    }
  }

  @Nullable
  private static String getConditional(PsiExpression condition,
                                       PsiElement thenBranch,
                                       PsiExpression thenReturnValue,
                                       PsiExpression elseReturnValue,
                                       CommentTracker commentTracker) {
    final PsiType methodType = PsiTypesUtil.getMethodReturnType(thenBranch);
    return methodType == null ? null : getConditionalText(condition, thenReturnValue, elseReturnValue, methodType, commentTracker);
  }

  private static String getConditionalText(PsiExpression condition,
                                           PsiExpression thenValue,
                                           PsiExpression elseValue,
                                           PsiType requiredType,
                                           CommentTracker commentTracker) {
    condition = ParenthesesUtils.stripParentheses(condition);
    thenValue = ParenthesesUtils.stripParentheses(thenValue);
    elseValue = ParenthesesUtils.stripParentheses(elseValue);

    thenValue = expandDiamondsWhenNeeded(thenValue, requiredType);
    if (thenValue == null) {
      return null;
    }
    elseValue = expandDiamondsWhenNeeded(elseValue, requiredType);
    if (elseValue == null) {
      return null;
    }
    @NonNls final StringBuilder conditional = new StringBuilder();
    final String conditionText = getExpressionText(condition, true);
    conditional.append(conditionText).append('?');
    final PsiType thenType = thenValue.getType();
    final PsiType elseType = elseValue.getType();
    if (thenType instanceof PsiPrimitiveType &&
        !PsiType.NULL.equals(thenType) &&
        !(elseType instanceof PsiPrimitiveType) &&
        !(requiredType instanceof PsiPrimitiveType)) {
      // prevent unboxing of boxed value to preserve semantics (IDEADEV-36008)
      final PsiPrimitiveType primitiveType = (PsiPrimitiveType)thenType;
      conditional.append(primitiveType.getBoxedTypeName());
      conditional.append(".valueOf(").append(commentTracker.text(thenValue)).append("):");
      conditional.append(getExpressionText(commentTracker.markUnchanged(elseValue), false));
    }
    else if (elseType instanceof PsiPrimitiveType &&
             !PsiType.NULL.equals(elseType) &&
             !(thenType instanceof PsiPrimitiveType) &&
             !(requiredType instanceof PsiPrimitiveType)) {
      // prevent unboxing of boxed value to preserve semantics (IDEADEV-36008)
      conditional.append(getExpressionText(commentTracker.markUnchanged(thenValue), false));
      conditional.append(':');
      final PsiPrimitiveType primitiveType = (PsiPrimitiveType)elseType;
      conditional.append(primitiveType.getBoxedTypeName());
      conditional.append(".valueOf(").append(commentTracker.text(elseValue)).append(')');
    }
    else {
      conditional.append(getExpressionText(commentTracker.markUnchanged(thenValue), false));
      conditional.append(':');
      conditional.append(getExpressionText(commentTracker.markUnchanged(elseValue), false));
    }
    return conditional.toString();
  }

  private static PsiExpression expandDiamondsWhenNeeded(PsiExpression thenValue, PsiType requiredType) {
    if (thenValue instanceof PsiNewExpression) {
      if (!PsiDiamondTypeUtil.canChangeContextForDiamond((PsiNewExpression)thenValue, requiredType)) {
        return PsiDiamondTypeUtil.expandTopLevelDiamondsInside(thenValue);
      }
    }
    return thenValue;
  }

  private static String getExpressionText(PsiExpression expression, boolean isCondition) {
    final int precedence = ParenthesesUtils.getPrecedence(expression);
    if (precedence <= ParenthesesUtils.CONDITIONAL_PRECEDENCE) {
      if (isCondition && precedence == ParenthesesUtils.CONDITIONAL_PRECEDENCE) {
        return '(' + expression.getText() + ')';
      }
      return expression.getText();
    }
    else {
      return '(' + expression.getText() + ')';
    }
  }
}
