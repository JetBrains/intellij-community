/*
 * Copyright 2009-2014 Bas Leijdekkers
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
package com.siyeh.ipp.opassign;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.base.PsiElementPredicate;

class ReplaceAssignmentWithPostfixExpressionPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiAssignmentExpression)) {
      return false;
    }
    final PsiAssignmentExpression assignmentExpression =
      (PsiAssignmentExpression)element;
    final PsiExpression lhs = assignmentExpression.getLExpression();
    final PsiExpression strippedLhs =
      ParenthesesUtils.stripParentheses(lhs);
    if (!(strippedLhs instanceof PsiReferenceExpression)) {
      return false;
    }
    final PsiReferenceExpression referenceExpression =
      (PsiReferenceExpression)strippedLhs;
    final PsiElement target = referenceExpression.resolve();
    if (!(target instanceof PsiVariable)) {
      return false;
    }
    final PsiVariable variable = (PsiVariable)target;
    final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getRExpression());
    if (!(rhs instanceof PsiBinaryExpression)) {
      return false;
    }
    final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)rhs;
    final PsiExpression lOperand = PsiUtil.skipParenthesizedExprDown(binaryExpression.getLOperand());
    final PsiExpression rOperand = PsiUtil.skipParenthesizedExprDown(binaryExpression.getROperand());
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    if (ExpressionUtils.isLiteral(lOperand, 1)) {
      if (!ExpressionUtils.isReferenceTo(rOperand, variable)) {
        return false;
      }
      return JavaTokenType.PLUS.equals(tokenType);
    }
    else if (ExpressionUtils.isLiteral(rOperand, 1)) {
      if (!ExpressionUtils.isReferenceTo(lOperand, variable)) {
        return false;
      }
      return JavaTokenType.PLUS.equals(tokenType) || JavaTokenType.MINUS.equals(tokenType);
    }
    return false;
  }
}