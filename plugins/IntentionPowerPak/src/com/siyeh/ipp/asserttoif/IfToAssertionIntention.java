/*
 * Copyright 2010-2013 Bas Leijdekkers
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
package com.siyeh.ipp.asserttoif;

import com.intellij.psi.*;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class IfToAssertionIntention extends Intention {

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new IfStatementPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiIfStatement)) {
      return;
    }
    final PsiIfStatement ifStatement = (PsiIfStatement)parent;
    final PsiExpression condition = ifStatement.getCondition();
    @NonNls final StringBuilder newStatementText = new StringBuilder("assert ");
    newStatementText.append(BoolUtils.getNegatedExpressionText(condition));
    final PsiStatement thenBranch = ifStatement.getThenBranch();
    final String message = getMessage(thenBranch);
    if (message != null) {
      newStatementText.append(':').append(message);
    }
    newStatementText.append(';');
    PsiReplacementUtil.replaceStatement(ifStatement, newStatementText.toString());
  }

  private static String getMessage(PsiElement element) {
    if (element instanceof PsiBlockStatement) {
      final PsiBlockStatement blockStatement = (PsiBlockStatement)element;
      final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      final PsiStatement[] statements = codeBlock.getStatements();
      if (statements.length != 1) {
        return null;
      }
      return getMessage(statements[0]);
    }
    else if (element instanceof PsiThrowStatement) {
      final PsiThrowStatement throwStatement = (PsiThrowStatement)element;
      final PsiExpression exception = ParenthesesUtils.stripParentheses(throwStatement.getException());
      if (!(exception instanceof PsiNewExpression)) {
        return null;
      }
      final PsiNewExpression newExpression = (PsiNewExpression)exception;
      final PsiExpressionList argumentList = newExpression.getArgumentList();
      if (argumentList == null) {
        return null;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return null;
      }
      return arguments[0].getText();
    }
    return null;
  }
}
