// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class IfElseExprSurrounder extends GroovyConditionSurrounder {
  @Override
  protected TextRange surroundExpression(@NotNull GrExpression expression, PsiElement context) {
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
    //noinspection DialogTitleCapitalization
    return GroovyBundle.message("surround.with.if.else.expr");
  }
}
