/*
 * Copyright 2007-2013 Bas Leijdekkers
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
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class ReplaceOperatorAssignmentWithAssignmentIntention extends MutablyNamedIntention {

  private static final Map<IElementType, IElementType> tokenMap = new HashMap<IElementType, IElementType>();

  static {
    tokenMap.put(JavaTokenType.PLUSEQ, JavaTokenType.PLUS);
    tokenMap.put(JavaTokenType.MINUSEQ, JavaTokenType.MINUS);
    tokenMap.put(JavaTokenType.ASTERISKEQ, JavaTokenType.ASTERISK);
    tokenMap.put(JavaTokenType.DIVEQ, JavaTokenType.DIV);
    tokenMap.put(JavaTokenType.ANDEQ, JavaTokenType.AND);
    tokenMap.put(JavaTokenType.OREQ, JavaTokenType.OR);
    tokenMap.put(JavaTokenType.XOREQ, JavaTokenType.XOR);
    tokenMap.put(JavaTokenType.PERCEQ, JavaTokenType.PERC);
    tokenMap.put(JavaTokenType.LTLTEQ, JavaTokenType.LTLT);
    tokenMap.put(JavaTokenType.GTGTEQ, JavaTokenType.GTGT);
    tokenMap.put(JavaTokenType.GTGTGTEQ, JavaTokenType.GTGTGT);
  }

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
    final boolean parentheses;
    if (rhs instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression binaryExpression = (PsiPolyadicExpression)rhs;
      final int precedence1 = ParenthesesUtils.getPrecedenceForOperator(binaryExpression.getOperationTokenType());
      final IElementType signTokenType = sign.getTokenType();
      final IElementType newOperatorToken = tokenMap.get(signTokenType);
      final int precedence2 = ParenthesesUtils.getPrecedenceForOperator(newOperatorToken);
      parentheses = precedence1 >= precedence2 || !ParenthesesUtils.isCommutativeOperator(newOperatorToken);
    }
    else {
      parentheses = false;
    }
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
    replaceExpression(newExpression.toString(), assignmentExpression);
  }

  private static String getCastString(PsiExpression lhs, PsiExpression rhs) {
    final PsiType lType = lhs.getType();
    final PsiType rType = rhs.getType();
    if (lType == null || rType == null ||
        TypeConversionUtil.isAssignable(lType, rType) || !TypeConversionUtil.areTypesConvertible(lType, rType)) {
      return "";
    }
    return '(' + lType.getCanonicalText() + ')';
  }
}