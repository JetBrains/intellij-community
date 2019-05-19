/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class WhileExprSurrounder extends GroovyConditionSurrounder {
  @Override
  protected TextRange surroundExpression(@NotNull GrExpression expression, PsiElement context) {
    GrWhileStatement whileStatement = (GrWhileStatement) GroovyPsiElementFactory.getInstance(expression.getProject()).createStatementFromText("while(a){4\n}", context);
    replaceToOldExpression(whileStatement.getCondition(), expression);
    whileStatement = expression.replaceWithStatement(whileStatement);
    GrStatement body = whileStatement.getBody();

    assert body instanceof GrBlockStatement;
    GrStatement[] statements = ((GrBlockStatement) body).getBlock().getStatements();
    assert statements.length > 0;

    GrStatement statement = statements[0];
    int offset = statement.getTextRange().getStartOffset();
    statement.getNode().getTreeParent().removeChild(statement.getNode());

    return new TextRange(offset, offset);
  }

  @Override
  public String getTemplateDescription() {
    return "while (expr)";
  }
}
