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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class WithExprSurrounder extends GroovyConditionSurrounder {
  @Override
  protected TextRange surroundExpression(GrExpression expression, PsiElement context) {
    GrMethodCallExpression call = (GrMethodCallExpression) GroovyPsiElementFactory.getInstance(expression.getProject()).createStatementFromText("with(a){4\n}", context);
    replaceToOldExpression(call.getExpressionArguments()[0], expression);
    call = expression.replaceWithStatement(call);
    GrClosableBlock block = call.getClosureArguments()[0];

    GrStatement statementInBody = block.getStatements()[0];
    int offset = statementInBody.getTextRange().getStartOffset();

    statementInBody.getParent().getNode().removeChild(statementInBody.getNode());

    return new TextRange(offset, offset);
  }

  @Override
  public String getTemplateDescription() {
    return "with (expr)";
  }
}