/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.intentions.closure;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;

/**
 * @author Maxim.Medvedev
 */
public class EachToForIntention extends Intention {
  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new EachToForPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) throws IncorrectOperationException {
    final GrMethodCallExpression expression = (GrMethodCallExpression)element;
    final GrClosableBlock block = expression.getClosureArguments()[0];
    final GrParameterList parameterList = block.getParameterList();
    final GrParameter[] parameters = parameterList.getParameters();

    String var;
    if (parameters.length == 1) {
      var = parameters[0].getText();
      var = StringUtil.replace(var, GrModifier.DEF, "");
    }
    else {
      var = "it";
    }

    final GrExpression invokedExpression = expression.getInvokedExpression();
    GrExpression qualifier = ((GrReferenceExpression)invokedExpression).getQualifierExpression();
    final GroovyPsiElementFactory elementFactory = GroovyPsiElementFactory.getInstance(element.getProject());
    if (qualifier == null) {
      qualifier = elementFactory.createExpressionFromText("this");
    }

    StringBuilder builder = new StringBuilder();
    builder.append("for (").append(var).append(" in ").append(qualifier.getText()).append(") {\n");
    String text = block.getText();
    final PsiElement blockArrow = block.getArrow();
    int index;
    if (blockArrow != null) {
      index = blockArrow.getStartOffsetInParent() + blockArrow.getTextLength();
    }
    else {
      index = 1;
    }
    while (index < text.length() && Character.isWhitespace(text.charAt(index))) index++;
    text = text.substring(index, text.length() - 1);
    builder.append(text);
    builder.append("}");

    final GrStatement statement = elementFactory.createStatementFromText(builder.toString());
    expression.replaceWithStatement(statement);
  }

  private static class EachToForPredicate implements PsiElementPredicate {
    public boolean satisfiedBy(PsiElement element) {
      if (element instanceof GrMethodCallExpression) {
        final GrMethodCallExpression expression = (GrMethodCallExpression)element;
//        final PsiElement parent = expression.getParent();
//        if (parent instanceof GrAssignmentExpression) return false;
//        if (parent instanceof GrArgumentList) return false;
//        if (parent instanceof GrReturnStatement) return false;
//        if (!(parent instanceof GrCodeBlock || parent instanceof GrIfStatement|| parent instanceof GrCaseSection)) return false;

        final GrExpression invokedExpression = expression.getInvokedExpression();
        if (invokedExpression instanceof GrReferenceExpression) {
          GrReferenceExpression referenceExpression = (GrReferenceExpression)invokedExpression;
          if ("each".equals(referenceExpression.getName())) {
            final GrArgumentList argumentList = expression.getArgumentList();
            if (argumentList != null) {
              if (argumentList.getExpressionArguments().length > 0) return false;
              if (argumentList.getNamedArguments().length > 0) return false;
            }
            final GrClosableBlock[] closureArguments = expression.getClosureArguments();
            if (closureArguments.length != 1) return false;
            final GrParameterList parameterList = closureArguments[0].getParameterList();
            if (parameterList == null) return false;

            final GrParameter[] parameters = parameterList.getParameters();
            if (parameters.length > 1) return false;
            return true;
          }
        }
      }
      return false;
    }
  }
}
