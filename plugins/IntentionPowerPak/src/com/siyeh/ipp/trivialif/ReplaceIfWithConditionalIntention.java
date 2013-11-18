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
package com.siyeh.ipp.trivialif;

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ConditionalUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

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
      final PsiExpressionStatement strippedThenBranch = (PsiExpressionStatement)ConditionalUtils.stripBraces(thenBranch);
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      final PsiExpressionStatement strippedElseBranch = (PsiExpressionStatement)ConditionalUtils.stripBraces(elseBranch);
      final PsiAssignmentExpression thenAssign = (PsiAssignmentExpression)strippedThenBranch.getExpression();
      final PsiAssignmentExpression elseAssign = (PsiAssignmentExpression)strippedElseBranch.getExpression();
      final PsiExpression lhs = thenAssign.getLExpression();
      final String lhsText = lhs.getText();
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
      final String conditional = getConditionalText(condition, thenRhs, elseRhs, thenAssign.getType());
      replaceStatement(lhsText + operator + conditional + ';', ifStatement);
    }
    else if (ReplaceIfWithConditionalPredicate.isReplaceableReturn(ifStatement)) {
      final PsiExpression condition = ifStatement.getCondition();
      if (condition == null) {
        return;
      }
      final PsiStatement thenBranch = ifStatement.getThenBranch();
      final PsiReturnStatement thenReturn = (PsiReturnStatement)ConditionalUtils.stripBraces(thenBranch);
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      final PsiReturnStatement elseReturn = (PsiReturnStatement)ConditionalUtils.stripBraces(elseBranch);
      final PsiExpression thenReturnValue = thenReturn.getReturnValue();
      if (thenReturnValue == null) {
        return;
      }
      final PsiExpression elseReturnValue = elseReturn.getReturnValue();
      if (elseReturnValue == null) {
        return;
      }
      final PsiMethod method = PsiTreeUtil.getParentOfType(thenReturn, PsiMethod.class);
      if (method == null) {
        return;
      }
      final PsiType returnType = method.getReturnType();
      final String conditional = getConditionalText(condition, thenReturnValue, elseReturnValue, returnType);
      replaceStatement("return " + conditional + ';', ifStatement);
    }
    else if (ReplaceIfWithConditionalPredicate.isReplaceableMethodCall(ifStatement)) {
      final PsiExpression condition = ifStatement.getCondition();
      if (condition == null) {
        return;
      }
      final PsiExpressionStatement thenBranch = (PsiExpressionStatement)ConditionalUtils.stripBraces(ifStatement.getThenBranch());
      final PsiExpressionStatement elseBranch = (PsiExpressionStatement)ConditionalUtils.stripBraces(ifStatement.getElseBranch());
      final PsiMethodCallExpression thenMethodCallExpression = (PsiMethodCallExpression)thenBranch.getExpression();
      final PsiMethodCallExpression elseMethodCallExpression = (PsiMethodCallExpression)elseBranch.getExpression();
      final StringBuilder replacementText = new StringBuilder(thenMethodCallExpression.getMethodExpression().getText());
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
        if (EquivalenceChecker.expressionsAreEquivalent(thenArgument, elseArgument)) {
          replacementText.append(thenArgument.getText());
        }
        else {
          final PsiMethod method = thenMethodCallExpression.resolveMethod();
          if (method == null) {
            return;
          }
          final PsiParameterList parameterList = method.getParameterList();
          final PsiType requiredType = parameterList.getParameters()[i].getType();
          final String conditionalText = getConditionalText(condition, thenArgument, elseArgument, requiredType);
          if (conditionalText == null) {
            return;
          }
          replacementText.append(conditionalText);
        }
      }
      replacementText.append(");");
      replaceStatement(replacementText.toString(), ifStatement);
    }
    else if (ReplaceIfWithConditionalPredicate.isReplaceableImplicitReturn(ifStatement)) {
      final PsiExpression condition = ifStatement.getCondition();
      if (condition == null) {
        return;
      }
      final PsiReturnStatement thenBranch = (PsiReturnStatement)ConditionalUtils.stripBraces(ifStatement.getThenBranch());
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
      final PsiMethod method = PsiTreeUtil.getParentOfType(thenBranch, PsiMethod.class);
      if (method == null) {
        return;
      }
      final PsiType methodType = method.getReturnType();
      final String conditional = getConditionalText(condition, thenReturnValue, elseReturnValue, methodType);
      if (conditional == null) {
        return;
      }
      replaceStatement("return " + conditional + ';', ifStatement);
      elseBranch.delete();
    }
  }

  private static String getConditionalText(PsiExpression condition,
                                           PsiExpression thenValue,
                                           PsiExpression elseValue,
                                           PsiType requiredType) {
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
      conditional.append(".valueOf(").append(thenValue.getText()).append("):");
      conditional.append(getExpressionText(elseValue, false));
    }
    else if (elseType instanceof PsiPrimitiveType &&
             !PsiType.NULL.equals(elseType) &&
             !(thenType instanceof PsiPrimitiveType) &&
             !(requiredType instanceof PsiPrimitiveType)) {
      // prevent unboxing of boxed value to preserve semantics (IDEADEV-36008)
      conditional.append(getExpressionText(thenValue, false));
      conditional.append(':');
      final PsiPrimitiveType primitiveType = (PsiPrimitiveType)elseType;
      conditional.append(primitiveType.getBoxedTypeName());
      conditional.append(".valueOf(").append(elseValue.getText()).append(')');
    }
    else {
      conditional.append(getExpressionText(thenValue, false));
      conditional.append(':');
      conditional.append(getExpressionText(elseValue, false));
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
