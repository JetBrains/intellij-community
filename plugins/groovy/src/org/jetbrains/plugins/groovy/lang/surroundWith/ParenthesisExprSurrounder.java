// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;

public class ParenthesisExprSurrounder extends GroovyExpressionSurrounder {
  @Override
  protected TextRange surroundExpression(@NotNull GrExpression expression, PsiElement context) {
    GrParenthesizedExpression result = (GrParenthesizedExpression) GroovyPsiElementFactory.getInstance(expression.getProject()).createExpressionFromText("(a)", context);
    replaceToOldExpression(result.getOperand(), expression);
    result = (GrParenthesizedExpression) expression.replaceWithExpression(result, true);
    return new TextRange(result.getTextRange().getEndOffset(), result.getTextRange().getEndOffset());
  }

  @Override
  public String getTemplateDescription() {
    //noinspection DialogTitleCapitalization
    return GroovyBundle.message("surround.with.parentheses");
  }
}
