/*
 * Copyright 2008-2012 Bas Leijdekkers
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
import com.intellij.psi.util.PsiConcatenationUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ConcatenationUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ReplaceConcatenationWithFormatStringIntention extends Intention {

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new Jdk5StringConcatenationPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) throws IncorrectOperationException {
    PsiPolyadicExpression expression = (PsiPolyadicExpression)element;
    PsiElement parent = expression.getParent();
    while (ConcatenationUtils.isConcatenation(parent)) {
      expression = (PsiPolyadicExpression)parent;
      parent = expression.getParent();
    }
    final StringBuilder formatString = new StringBuilder();
    final List<PsiExpression> formatParameters = new ArrayList();
    PsiConcatenationUtil.buildFormatString(expression, formatString, formatParameters, true);
    if (replaceWithPrintfExpression(expression, formatString, formatParameters)) {
      return;
    }
    final StringBuilder newExpression = new StringBuilder();
    newExpression.append("java.lang.String.format(\"");
    newExpression.append(formatString);
    newExpression.append('\"');
    for (PsiExpression formatParameter : formatParameters) {
      newExpression.append(", ");
      newExpression.append(formatParameter.getText());
    }
    newExpression.append(')');
    PsiReplacementUtil.replaceExpression(expression, newExpression.toString());
  }

  private static boolean replaceWithPrintfExpression(PsiExpression expression, CharSequence formatString,
                                                     List<PsiExpression> formatParameters) throws IncorrectOperationException {
    final PsiElement expressionParent = expression.getParent();
    if (!(expressionParent instanceof PsiExpressionList)) {
      return false;
    }
    final PsiElement grandParent = expressionParent.getParent();
    if (!(grandParent instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    final String name = methodExpression.getReferenceName();
    final boolean insertNewline;
    if ("println".equals(name)) {
      insertNewline = true;
    }
    else if ("print".equals(name)) {
      insertNewline = false;
    }
    else {
      return false;
    }
    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) {
      return false;
    }
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    final String qualifiedName = containingClass.getQualifiedName();
    if (!"java.io.PrintStream".equals(qualifiedName) &&
        !"java.io.Printwriter".equals(qualifiedName)) {
      return false;
    }
    final StringBuilder newExpression = new StringBuilder();
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (qualifier != null) {
      newExpression.append(qualifier.getText());
      newExpression.append('.');
    }
    newExpression.append("printf(\"");
    newExpression.append(formatString);
    if (insertNewline) {
      newExpression.append("%n");
    }
    newExpression.append('\"');
    for (PsiExpression formatParameter : formatParameters) {
      newExpression.append(", ");
      newExpression.append(formatParameter.getText());
    }
    newExpression.append(')');
    PsiReplacementUtil.replaceExpression(methodCallExpression, newExpression.toString());
    return true;
  }
}
