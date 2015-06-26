/*
 * Copyright 2007-2015 Bas Leijdekkers
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
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ReplaceOperatorAssignmentWithAssignmentIntention extends MutablyNamedIntention {

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new OperatorAssignmentPredicate();
  }

  @Override
  protected String getTextForElement(PsiElement element) {
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)element;
    final PsiJavaToken sign = assignmentExpression.getOperationSign();
    final String operator = sign.getText();
    return IntentionPowerPackBundle.message("replace.operator.assignment.with.assignment.intention.name", operator);
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)element;
    final PsiJavaToken sign = assignmentExpression.getOperationSign();
    final PsiExpression lhs = assignmentExpression.getLExpression();
    final PsiExpression rhs = assignmentExpression.getRExpression();
    final String operator = sign.getText();
    final String newOperator = operator.substring(0, operator.length() - 1);
    final String lhsText = lhs.getText();
    final String rhsText = (rhs == null) ? "" : rhs.getText();
    final boolean parentheses = ParenthesesUtils.areParenthesesNeeded(sign, rhs);
    final String cast = getCastString(lhs, rhs);
    final StringBuilder newExpression = new StringBuilder(lhsText);
    newExpression.append('=').append(cast);
    if (!cast.isEmpty()) {
      newExpression.append('(');
    }
    newExpression.append(lhsText).append(newOperator);
    if (parentheses) {
      newExpression.append('(').append(rhsText).append(')');
    }
    else {
      newExpression.append(rhsText);
    }
    if (!cast.isEmpty()) {
      newExpression.append(')');
    }
    PsiReplacementUtil.replaceExpression(assignmentExpression, newExpression.toString());
  }

  private static String getCastString(PsiExpression lhs, PsiExpression rhs) {
    if (lhs == null || rhs == null) {
      return "";
    }
    final PsiType lType = lhs.getType();
    PsiType rType = rhs.getType();
    if (TypeConversionUtil.isNumericType(rType)) {
      rType = TypeConversionUtil.binaryNumericPromotion(lType, rType);
    }
    if (lType == null || rType == null ||
        TypeConversionUtil.isAssignable(lType, rType) || !TypeConversionUtil.areTypesConvertible(lType, rType)) {
      return "";
    }
    return '(' + lType.getCanonicalText() + ')';
  }
}