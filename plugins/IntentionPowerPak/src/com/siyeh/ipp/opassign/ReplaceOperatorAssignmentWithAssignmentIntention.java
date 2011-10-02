/*
 * Copyright 2007-2009 Bas Leijdekkers
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
import com.intellij.util.IncorrectOperationException;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.HashMap;

public class ReplaceOperatorAssignmentWithAssignmentIntention
  extends MutablyNamedIntention {

  private static final Map<IElementType, IElementType> tokenMap = new HashMap();

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
    final PsiAssignmentExpression assignmentExpression =
      (PsiAssignmentExpression)element;
    final PsiJavaToken sign = assignmentExpression.getOperationSign();
    final String operator = sign.getText();
    return IntentionPowerPackBundle.message(
      "replace.operator.assignment.with.assignment.intention.name",
      operator);
  }

  @Override
  protected void processIntention(@NotNull PsiElement element)
    throws IncorrectOperationException {
    final PsiAssignmentExpression assignmentExpression =
      (PsiAssignmentExpression)element;
    final PsiJavaToken sign = assignmentExpression.getOperationSign();
    final PsiExpression lhs = assignmentExpression.getLExpression();
    final PsiExpression rhs = assignmentExpression.getRExpression();
    final String operator = sign.getText();
    final String newOperator = operator.substring(0, operator.length() - 1);
    final String lhsText = lhs.getText();
    final String rhsText;
    if (rhs == null) {
      rhsText = "";
    }
    else {
      rhsText = rhs.getText();
    }
    if (rhs instanceof PsiBinaryExpression) {
      final PsiBinaryExpression binaryExpression =
        (PsiBinaryExpression)rhs;
      final int precedence1 =
        ParenthesesUtils.getPrecedenceForBinaryOperator(binaryExpression.getOperationTokenType());
      final IElementType signTokenType = sign.getTokenType();
      final IElementType newOperatorToken = tokenMap.get(signTokenType);
      final int precedence2 =
        ParenthesesUtils.getPrecedenceForBinaryOperator(
          newOperatorToken);
      if (precedence1 > precedence2 ||
          !ParenthesesUtils.isCommutativeBinaryOperator(
            newOperatorToken)) {
        final String expString;
        if (needsCast(rhs)) {
          expString = lhsText + "=(int)" + lhsText + newOperator
                      + '(' + rhsText + "))";
        }
        else {
          expString = lhsText + '=' + lhsText + newOperator
                      + '(' + rhsText + ')';
        }
        replaceExpression(expString, assignmentExpression);
        return;
      }
    }
    final String expString;
    if (needsCast(rhs)) {
      expString = lhsText + "=(int)(" + lhsText + newOperator + rhsText
                  + ')';
    }
    else {
      expString = lhsText + '=' + lhsText + newOperator + rhsText;
    }
    replaceExpression(expString, assignmentExpression);
  }

  private static boolean needsCast(PsiExpression expression) {
    final PsiType type = expression.getType();
    return PsiType.LONG.equals(type) || PsiType.DOUBLE.equals(type) ||
           PsiType.FLOAT.equals(type);
  }
}