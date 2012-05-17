/*
 * Copyright 2009-2012 Bas Leijdekkers
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
import com.siyeh.ipp.base.PsiElementPredicate;

class ReplaceOperatorAssignmentWithPostfixExpressionPredicate implements PsiElementPredicate {

  private static final Integer ONE = Integer.valueOf(1);

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiAssignmentExpression)) {
      return false;
    }
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)element;
    final IElementType tokenType = assignmentExpression.getOperationTokenType();
    if (!JavaTokenType.PLUSEQ.equals(tokenType) && !JavaTokenType.MINUSEQ.equals(tokenType)) {
      return false;
    }
    final PsiExpression rhs = assignmentExpression.getRExpression();
    if (!(rhs instanceof PsiLiteralExpression)) {
      return false;
    }
    final PsiLiteralExpression literalExpression = (PsiLiteralExpression)rhs;
    final Object value = literalExpression.getValue();
    return ONE == value;
  }
}