/*
 * Copyright 2009-2018 Bas Leijdekkers
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
package com.siyeh.ipp.forloop;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ReverseForLoopDirectionIntention extends Intention {

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new ReverseForLoopDirectionPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiForStatement forStatement =
      (PsiForStatement)element.getParent();
    final PsiDeclarationStatement initialization =
      (PsiDeclarationStatement)forStatement.getInitialization();
    if (initialization == null) {
      return;
    }
    final PsiBinaryExpression condition =
      (PsiBinaryExpression)forStatement.getCondition();
    if (condition == null) {
      return;
    }
    final PsiLocalVariable variable =
      (PsiLocalVariable)initialization.getDeclaredElements()[0];
    final PsiExpression initializer = variable.getInitializer();
    if (initializer == null) {
      return;
    }
    final PsiExpression lhs = condition.getLOperand();
    final PsiExpression rhs = condition.getROperand();
    if (rhs == null) {
      return;
    }
    final PsiExpressionStatement update =
      (PsiExpressionStatement)forStatement.getUpdate();
    if (update == null) {
      return;
    }
    final PsiExpression updateExpression = update.getExpression();
    final String variableName = variable.getName();
    final StringBuilder newUpdateText = new StringBuilder();
    if (updateExpression instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixExpression =
        (PsiPrefixExpression)updateExpression;
      final IElementType tokenType =
        prefixExpression.getOperationTokenType();
      if (JavaTokenType.PLUSPLUS == tokenType) {
        newUpdateText.append("--");
      }
      else if (JavaTokenType.MINUSMINUS == tokenType) {
        newUpdateText.append("++");
      }
      else {
        return;
      }
      newUpdateText.append(variableName);
    }
    else if (updateExpression instanceof PsiPostfixExpression) {
      newUpdateText.append(variableName);
      final PsiPostfixExpression postfixExpression =
        (PsiPostfixExpression)updateExpression;
      final IElementType tokenType =
        postfixExpression.getOperationTokenType();
      if (JavaTokenType.PLUSPLUS == tokenType) {
        newUpdateText.append("--");
      }
      else if (JavaTokenType.MINUSMINUS == tokenType) {
        newUpdateText.append("++");
      }
      else {
        return;
      }
    }
    else {
      return;
    }
    final Project project = element.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final IElementType sign = condition.getOperationTokenType();
    final String negatedSign = ComparisonUtils.getNegatedComparison(sign);
    final StringBuilder conditionText = new StringBuilder();
    final StringBuilder newInitializerText = new StringBuilder();
    if (VariableAccessUtils.evaluatesToVariable(lhs, variable)) {
      conditionText.append(variableName);
      conditionText.append(negatedSign);
      if (sign == JavaTokenType.GE) {
        conditionText.append(incrementExpression(initializer, true));
      }
      else if (sign == JavaTokenType.LE) {
        conditionText.append(incrementExpression(initializer, false));
      }
      else {
        conditionText.append(initializer.getText());
      }
      if (sign == JavaTokenType.LT) {
        newInitializerText.append(incrementExpression(rhs, false));
      }
      else if (sign == JavaTokenType.GT) {
        newInitializerText.append(incrementExpression(rhs, true));
      }
      else {
        newInitializerText.append(rhs.getText());
      }
    }
    else if (VariableAccessUtils.evaluatesToVariable(rhs, variable)) {
      if (sign == JavaTokenType.LE) {
        conditionText.append(incrementExpression(initializer, true));
      }
      else if (sign == JavaTokenType.GE) {
        conditionText.append(incrementExpression(initializer, false));
      }
      else {
        conditionText.append(initializer.getText());
      }
      conditionText.append(negatedSign);
      conditionText.append(variableName);
      if (sign == JavaTokenType.GT) {
        newInitializerText.append(incrementExpression(lhs, false));
      }
      else if (sign == JavaTokenType.LT) {
        newInitializerText.append(incrementExpression(lhs, true));
      }
      else {
        newInitializerText.append(lhs.getText());
      }
    }
    else {
      return;
    }
    final PsiExpression newInitializer = factory.createExpressionFromText(newInitializerText.toString(), element);
    variable.setInitializer(newInitializer);
    final PsiExpression newCondition = factory.createExpressionFromText(conditionText.toString(), element);
    condition.replace(newCondition);
    final PsiExpression newUpdate = factory.createExpressionFromText(newUpdateText.toString(), element);
    updateExpression.replace(newUpdate);
  }

  private static String incrementExpression(PsiExpression expression,
                                            boolean positive) {
    if (expression instanceof PsiLiteralExpression) {
      final PsiLiteralExpression literalExpression =
        (PsiLiteralExpression)expression;
      final Number value = (Number)literalExpression.getValue();
      if (value == null) {
        return null;
      }
      if (positive) {
        return String.valueOf(value.longValue() + 1L);
      }
      else {
        return String.valueOf(value.longValue() - 1L);
      }
    }
    else {
      if (expression instanceof PsiBinaryExpression) {
        // see if we can remove a -1 instead of adding a +1
        final PsiBinaryExpression binaryExpression =
          (PsiBinaryExpression)expression;
        final PsiExpression rhs = binaryExpression.getROperand();
        if (ExpressionUtils.isOne(rhs)) {
          final IElementType tokenType =
            binaryExpression.getOperationTokenType();
          if (tokenType == JavaTokenType.MINUS && positive) {
            return binaryExpression.getLOperand().getText();
          }
          else if (tokenType == JavaTokenType.PLUS && !positive) {
            return binaryExpression.getLOperand().getText();
          }
        }
      }
      final String expressionText;
      if (ParenthesesUtils.getPrecedence(expression) >
          ParenthesesUtils.ADDITIVE_PRECEDENCE) {
        expressionText = '(' + expression.getText() + ')';
      }
      else {
        expressionText = expression.getText();
      }
      if (positive) {
        return expressionText + "+1";
      }
      else {
        return expressionText + "-1";
      }
    }
  }
}