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
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ipp.base.PsiElementPredicate;

class IfStatementPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiJavaToken)) {
      return false;
    }
    final PsiJavaToken token = (PsiJavaToken)element;
    if (token.getTokenType() != JavaTokenType.IF_KEYWORD) {
      return false;
    }
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiIfStatement)) {
      return false;
    }
    final PsiIfStatement statement = (PsiIfStatement)parent;
    final PsiStatement elseBranch = statement.getElseBranch();
    if (elseBranch != null) {
      return false;
    }
    final PsiStatement thenBranch = statement.getThenBranch();
    return throwsException(thenBranch);
  }

  private static boolean throwsException(PsiStatement element) {
    if (element instanceof PsiThrowStatement) {
      final PsiThrowStatement throwStatement = (PsiThrowStatement)element;
      final PsiExpression exception = ParenthesesUtils.stripParentheses(throwStatement.getException());
      if (!(exception instanceof PsiNewExpression)) {
        return false;
      }
      final PsiNewExpression newExpression = (PsiNewExpression)exception;
      return TypeUtils.expressionHasTypeOrSubtype(newExpression, CommonClassNames.JAVA_LANG_THROWABLE);
    }
    else if (element instanceof PsiBlockStatement) {
      final PsiBlockStatement blockStatement = (PsiBlockStatement)element;
      final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      final PsiStatement[] statements = codeBlock.getStatements();
      if (statements.length != 1) {
        return false;
      }
      final PsiStatement statement = statements[0];
      return throwsException(statement);
    }
    return false;
  }
}
