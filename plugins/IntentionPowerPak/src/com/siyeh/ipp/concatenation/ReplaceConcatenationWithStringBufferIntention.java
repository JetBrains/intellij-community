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
package com.siyeh.ipp.concatenation;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ReplaceConcatenationWithStringBufferIntention extends MutablyNamedIntention {

  @Override
  protected String getTextForElement(PsiElement element) {
    if (PsiUtil.isLanguageLevel5OrHigher(element)) {
      return IntentionPowerPackBundle.message(
        "replace.concatenation.with.string.builder.intention.name");
    }
    else {
      return IntentionPowerPackBundle.message(
        "replace.concatenation.with.string.buffer.intention.name");
    }
  }

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new SimpleStringConcatenationPredicate(true);
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    PsiPolyadicExpression expression = (PsiPolyadicExpression)element;
    PsiElement parent = expression.getParent();
    while (ExpressionUtils.isConcatenation(parent)) {
      expression = (PsiPolyadicExpression)parent;
      parent = expression.getParent();
    }
    CommentTracker commentTracker = new CommentTracker();
    @NonNls final StringBuilder newExpression = new StringBuilder();
    if (isPartOfStringBufferAppend(expression)) {
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)parent.getParent();
      assert methodCallExpression != null;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();

      if (qualifierExpression != null) {
        final String qualifierText = commentTracker.text(qualifierExpression);
        newExpression.append(qualifierText);
      }
      turnExpressionIntoChainedAppends(expression, newExpression, commentTracker);
      PsiReplacementUtil.replaceExpression(methodCallExpression, newExpression.toString(), commentTracker);
    }
    else {
      if (!PsiUtil.isLanguageLevel5OrHigher(expression)) {
        newExpression.append("new StringBuffer()");
      }
      else {
        newExpression.append("new StringBuilder()");
      }
      turnExpressionIntoChainedAppends(expression, newExpression, commentTracker);
      newExpression.append(".toString()");
      PsiReplacementUtil.replaceExpression(expression, newExpression.toString(), commentTracker);
    }
  }

  private static boolean isPartOfStringBufferAppend(PsiExpression expression) {
    PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiExpressionList)) {
      return false;
    }
    parent = parent.getParent();
    if (!(parent instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)parent;
    final PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
    final PsiType type = methodExpression.getType();
    if (type == null) {
      return false;
    }
    final String className = type.getCanonicalText();
    if (!CommonClassNames.JAVA_LANG_STRING_BUFFER.equals(className) && !CommonClassNames.JAVA_LANG_STRING_BUILDER.equals(className)) {
      return false;
    }
    @NonNls final String methodName = methodExpression.getReferenceName();
    return "append".equals(methodName);
  }

  private static void turnExpressionIntoChainedAppends(PsiExpression expression,
                                                       @NonNls StringBuilder result,
                                                       CommentTracker commentTracker) {
    if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression concatenation = (PsiPolyadicExpression)expression;
      final PsiType type = concatenation.getType();
      if (type != null && !type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        result.append(".append(").append(commentTracker.text(concatenation)).append(')');
        return;
      }
      final PsiExpression[] operands = concatenation.getOperands();
      final PsiType startType = operands[0].getType();
      if (startType == null || startType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        for (PsiExpression operand : operands) {
          turnExpressionIntoChainedAppends(operand, result, commentTracker);
        }
        return;
      }
      final StringBuilder newExpressionText = new StringBuilder(operands[0].getText());
      boolean string = false;
      for (int i = 1; i < operands.length; i++) {
        final PsiExpression operand = operands[i];
        if (!string) {
          final PsiType operandType = operand.getType();
          if (operandType == null || operandType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
            final PsiElementFactory factory = JavaPsiFacade.getElementFactory(expression.getProject());
            final PsiExpression newExpression = factory.createExpressionFromText(newExpressionText.toString(), expression);
            turnExpressionIntoChainedAppends(newExpression, result, commentTracker);
            turnExpressionIntoChainedAppends(operand, result, commentTracker);
            string = true;
          }
          newExpressionText.append('+').append(commentTracker.text(operand));
        }
        else {
          turnExpressionIntoChainedAppends(operand, result, commentTracker);
        }
      }
    }
    else {
      final PsiExpression strippedExpression = ParenthesesUtils.stripParentheses(expression);
      result.append(".append(");
      if (strippedExpression != null) {
        result.append(commentTracker.text(strippedExpression));
      }
      result.append(')');
    }
  }
}
