/*
 * Copyright 2008 Bas Leijdekkers
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
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

class RemoveParenthesesFromMethodPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof GrMethodCallExpression)) return false;
    if (!PsiUtil.isExpressionStatement(element)) return false;

    final GrMethodCallExpression expression = (GrMethodCallExpression)element;

    if (PsiImplUtil.hasClosureArguments(expression)) return false;

    final StringBuilder newStatementText = new StringBuilder();
    newStatementText.append(expression.getInvokedExpression().getText()).append(' ');
    GrArgumentList argumentList = expression.getArgumentList();

    final GroovyPsiElement[] allArguments = argumentList != null ? argumentList.getAllArguments() : GroovyPsiElement.EMPTY_ARRAY;

    if (argumentList != null) {
      argumentList = (GrArgumentList)argumentList.copy();
      final PsiElement leftParen = argumentList.getLeftParen();
      final PsiElement rightParen = argumentList.getRightParen();
      if (leftParen != null) leftParen.delete();
      if (rightParen != null) rightParen.delete();
      newStatementText.append(argumentList.getText());
    }
    final GrStatement newStatement;
    try {
      newStatement = GroovyPsiElementFactory.getInstance(element.getProject()).createStatementFromText(newStatementText.toString());
    }
    catch (IncorrectOperationException e) {
      return false;
    }
    if (newStatement instanceof GrApplicationStatement) {
      final GrCommandArgumentList newArgList = ((GrApplicationStatement)newStatement).getArgumentList();
      if (newArgList == null && argumentList == null ||
          newArgList != null && newArgList.getAllArguments().length == allArguments.length) {
        return true;
      }
    }

    return false;
  }
}