// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTypeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

public class TypeCastSurrounder extends GroovyExpressionSurrounder {
  @Override
  protected TextRange surroundExpression(@NotNull GrExpression expression, PsiElement context) {
    GrParenthesizedExpression parenthesized = (GrParenthesizedExpression) GroovyPsiElementFactory.getInstance(expression.getProject()).createExpressionFromText("((Type)a)", context);
    GrTypeCastExpression typeCast = (GrTypeCastExpression) parenthesized.getOperand();
    replaceToOldExpression(typeCast.getOperand(), expression);
    GrTypeElement typeElement = typeCast.getCastTypeElement();
    int endOffset = typeElement.getTextRange().getStartOffset() + expression.getTextRange().getStartOffset();
    parenthesized = (GrParenthesizedExpression) expression.replaceWithExpression(parenthesized, false);

    final GrTypeCastExpression newTypeCast = (GrTypeCastExpression)parenthesized.getOperand();
    final GrTypeElement newTypeElement = newTypeCast.getCastTypeElement();
    newTypeElement.delete();
    return new TextRange(endOffset, endOffset);
  }

  @Override
  public String getTemplateDescription() {
    //noinspection DialogTitleCapitalization
    return GroovyBundle.message("surround.with.cast");
  }
}
