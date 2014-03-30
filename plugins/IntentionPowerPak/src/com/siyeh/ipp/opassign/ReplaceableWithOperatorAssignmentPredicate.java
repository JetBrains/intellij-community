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
package com.siyeh.ipp.opassign;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;

class ReplaceableWithOperatorAssignmentPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiAssignmentExpression)) {
      return false;
    }
    final PsiAssignmentExpression assignment = (PsiAssignmentExpression)element;
    final IElementType tokenType = assignment.getOperationTokenType();
    if (!JavaTokenType.EQ.equals(tokenType)) {
      return false;
    }
    final PsiExpression rhs = assignment.getRExpression();
    final PsiExpression expression = PsiUtil.deparenthesizeExpression(rhs);
    if (!(expression instanceof PsiPolyadicExpression)) {
      return false;
    }
    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
    final PsiExpression[] operands = polyadicExpression.getOperands();
    if (operands.length < 2) {
      return false;
    }
    final IElementType rhsTokenType = polyadicExpression.getOperationTokenType();
    if (JavaTokenType.OROR.equals(rhsTokenType) || JavaTokenType.ANDAND.equals(rhsTokenType) ||
        JavaTokenType.EQEQ.equals(rhsTokenType) || JavaTokenType.NE.equals(rhsTokenType)) {
      return false;
    }
    final PsiExpression lhs = assignment.getLExpression();
    if (SideEffectChecker.mayHaveSideEffects(lhs)) {
      return false;
    }
    if (!EquivalenceChecker.expressionsAreEquivalent(lhs, operands[0])) {
      return false;
    }
    return !ErrorUtil.containsError(element);
  }
}