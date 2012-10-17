/*
 * Copyright 2010 Bas Leijdekkers
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
    return isThrowNewAssertionError(thenBranch);
  }

  private static boolean isThrowNewAssertionError(PsiElement element) {
    if (element instanceof PsiThrowStatement) {
      final PsiThrowStatement throwStatement =
        (PsiThrowStatement)element;
      final PsiExpression exception = throwStatement.getException();
      if (!(exception instanceof PsiNewExpression)) {
        return false;
      }
      final PsiNewExpression newExpression = (PsiNewExpression)exception;
      final PsiJavaCodeReferenceElement classReference =
        newExpression.getClassReference();
      if (classReference == null) {
        return false;
      }
      final PsiElement target = classReference.resolve();
      if (!(target instanceof PsiClass)) {
        return false;
      }
      final PsiClass aClass = (PsiClass)target;
      final String qualifiedName = aClass.getQualifiedName();
      return CommonClassNames.JAVA_LANG_ASSERTION_ERROR.equals(qualifiedName);
    }
    else if (element instanceof PsiBlockStatement) {
      final PsiBlockStatement blockStatement =
        (PsiBlockStatement)element;
      final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      final PsiStatement[] statements = codeBlock.getStatements();
      if (statements.length != 1) {
        return false;
      }
      final PsiStatement statement = statements[0];
      return isThrowNewAssertionError(statement);
    }
    return false;
  }
}
