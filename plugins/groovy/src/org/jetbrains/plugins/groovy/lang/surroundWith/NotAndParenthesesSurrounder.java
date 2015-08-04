/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;

/**
 * @author Max Medvedev
 */
public class NotAndParenthesesSurrounder extends GroovyExpressionSurrounder {

  @Override
  protected TextRange surroundExpression(@NotNull GrExpression expression, @NotNull PsiElement context) {
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(expression.getProject());
    final GrUnaryExpression template = (GrUnaryExpression)factory.createExpressionFromText("!(a)", context);
    assert template.getOperand() != null;
    GroovyExpressionSurrounder.replaceToOldExpression(((GrParenthesizedExpression)template.getOperand()).getOperand(), expression);
    final GrExpression result = expression.replaceWithExpression(template, true);
    final int endOffset = result.getTextRange().getEndOffset();
    return new TextRange(endOffset, endOffset);
  }

  @Override
  public String getTemplateDescription() {
    return "!(expr)";
  }
}
