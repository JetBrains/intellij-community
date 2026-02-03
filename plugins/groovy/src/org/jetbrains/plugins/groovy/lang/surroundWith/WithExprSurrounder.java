// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

public class WithExprSurrounder extends GroovyConditionSurrounder {
  @Override
  protected TextRange surroundExpression(@NotNull GrExpression expression, PsiElement context) {
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
    //noinspection DialogTitleCapitalization
    return GroovyBundle.message("surround.with.with.expr");
  }
}