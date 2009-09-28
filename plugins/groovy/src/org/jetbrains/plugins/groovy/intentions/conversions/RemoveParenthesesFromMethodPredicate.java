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
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

class RemoveParenthesesFromMethodPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof GrMethodCallExpression)) {
      return false;
    }
    final GrMethodCallExpression methodCallExpression = (GrMethodCallExpression)element;
    final GrArgumentList argumentList = methodCallExpression.getArgumentList();
    if (argumentList == null) return false;

    final GrExpression[] arguments = argumentList.getExpressionArguments();
    if (arguments.length == 0) return false;

    GrExpression firstArg = arguments[0];
    if (firstArg instanceof GrListOrMap) return false;

    final PsiElement parent = element.getParent();

    if (firstArg instanceof GrMethodCallExpression) {
      GrMethodCallExpression call = (GrMethodCallExpression)firstArg;

      GrExpression invokedExpression = call.getInvokedExpression();

      if (invokedExpression instanceof GrReferenceExpression) {
        invokedExpression = getDeepestInvocationExpression(invokedExpression);
      }

      if (invokedExpression instanceof GrParenthesizedExpression) return false;
    }

    return parent instanceof GrOpenBlock || parent instanceof GroovyFile || parent instanceof GrClosableBlock;
  }

  private GrExpression getDeepestInvocationExpression(GrExpression invokedExpression) {
    if (invokedExpression instanceof GrReferenceExpression) {
      GrReferenceExpression refElement = (GrReferenceExpression)invokedExpression;

      return getDeepestInvocationExpression(refElement.getQualifierExpression());
    }
    return invokedExpression;
  }
}