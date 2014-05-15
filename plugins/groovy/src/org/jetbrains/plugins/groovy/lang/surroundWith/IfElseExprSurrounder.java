/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class IfElseExprSurrounder extends GroovyConditionSurrounder {
  @Override
  protected TextRange surroundExpression(GrExpression expression, PsiElement context) {
    GrIfStatement ifStatement = (GrIfStatement) GroovyPsiElementFactory.getInstance(expression.getProject()).createStatementFromText("if(a){4\n} else{\n}", context);
    replaceToOldExpression(ifStatement.getCondition(), expression);
    ifStatement = expression.replaceWithStatement(ifStatement);
    GrStatement psiElement = ifStatement.getThenBranch();


    assert psiElement instanceof GrBlockStatement;
    GrStatement[] statements = ((GrBlockStatement) psiElement).getBlock().getStatements();
    assert statements.length > 0;

    GrStatement statement = statements[0];
    int endOffset = statement.getTextRange().getStartOffset();
    statement.getNode().getTreeParent().removeChild(statement.getNode());

    return new TextRange(endOffset, endOffset);
  }

  @Override
  public String getTemplateDescription() {
    return "if (expr) / else";
  }
}
