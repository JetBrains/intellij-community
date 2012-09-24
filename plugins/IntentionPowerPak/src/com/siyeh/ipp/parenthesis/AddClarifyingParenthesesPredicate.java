/*
 * Copyright 2006-2012 Bas Leijdekkers
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
package com.siyeh.ipp.parenthesis;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class AddClarifyingParenthesesPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(@NotNull PsiElement element) {
    final PsiElement parent = element.getParent();
    if (mightBeConfusingExpression(parent)) {
      return false;
    }
    if (element instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)element;
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      final PsiExpression[] operands = polyadicExpression.getOperands();
      for (PsiExpression operand : operands) {
        if (operand instanceof PsiInstanceOfExpression) {
          return true;
        }
        if (!(operand instanceof PsiPolyadicExpression)) {
          continue;
        }
        final PsiPolyadicExpression expression = (PsiPolyadicExpression)operand;
        final IElementType otherTokenType = expression.getOperationTokenType();
        if (!tokenType.equals(otherTokenType)) {
          return true;
        }
      }
    }
    else if (element instanceof PsiConditionalExpression) {
      final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)element;
      final PsiExpression condition = conditionalExpression.getCondition();
      if (mightBeConfusingExpression(condition)) {
        return true;
      }
      final PsiExpression thenExpression = conditionalExpression.getThenExpression();
      if (mightBeConfusingExpression(thenExpression)) {
        return true;
      }
      final PsiExpression elseExpression = conditionalExpression.getElseExpression();
      if (mightBeConfusingExpression(elseExpression)) {
        return true;
      }
    }
    else if (element instanceof PsiInstanceOfExpression) {
      final PsiInstanceOfExpression instanceOfExpression = (PsiInstanceOfExpression)element;
      final PsiExpression operand = instanceOfExpression.getOperand();
      if (mightBeConfusingExpression(operand)) {
        return true;
      }
    }
    else if (element instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)element;
      final PsiExpression rhs = assignmentExpression.getRExpression();
      if (!(mightBeConfusingExpression(rhs))) {
        return false;
      }
      if (rhs instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression nestedAssignment = (PsiAssignmentExpression)rhs;
        final IElementType nestedTokenType = nestedAssignment.getOperationTokenType();
        final IElementType tokenType = assignmentExpression.getOperationTokenType();
        if (nestedTokenType.equals(tokenType)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  private static boolean mightBeConfusingExpression(@Nullable PsiElement element) {
    return element instanceof PsiPolyadicExpression || element instanceof PsiConditionalExpression ||
           element instanceof PsiInstanceOfExpression || element instanceof PsiAssignmentExpression;
  }
}