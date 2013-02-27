/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.surroundWith

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression

/**
 * @author Max Medvedev
 */
class NotAndParenthesesSurrounder extends GroovyExpressionSurrounder {
  @Override
  protected TextRange surroundExpression(GrExpression expression, PsiElement context) {
    GrUnaryExpression result = (GrUnaryExpression)GroovyPsiElementFactory.getInstance(expression.project).createExpressionFromText("!(a)", context)
    replaceToOldExpression(((GrParenthesizedExpression)result.operand).operand, expression)
    result = (GrUnaryExpression)expression.replaceWithExpression(result, true);
    return new TextRange(result.textRange.endOffset, result.textRange.endOffset);

  }

  @Override
  String getTemplateDescription() {
    "!(expr)"
  }
}
