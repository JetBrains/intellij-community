/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.metrics;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

class NegationCountVisitor extends GroovyRecursiveElementVisitor {

  private int negationCount = 0;

  @Override
  public void visitElement(GroovyPsiElement element) {
    int oldCount = 0;
    if (element instanceof GrMethod) {
      oldCount = negationCount;
    }
    super.visitElement(element);

    if (element instanceof GrMethod) {
      negationCount = oldCount;
    }
  }

  @Override
  public void visitBinaryExpression(@NotNull GrBinaryExpression expression) {
    super.visitBinaryExpression(expression);
    final IElementType tokenType = expression.getOperationTokenType();
    final GrExpression rhs = expression.getRightOperand();
    if (rhs == null) {
      return;
    }
    if (GroovyTokenTypes.mNOT_EQUAL.equals(tokenType)) {
      negationCount++;
    }
  }

  @Override
  public void visitUnaryExpression(GrUnaryExpression grUnaryExpression) {
    super.visitUnaryExpression(grUnaryExpression);
    final IElementType sign = grUnaryExpression.getOperationTokenType();
    if (GroovyTokenTypes.mLNOT.equals(sign)) {
      negationCount++;
    }
  }

  public int getNegationCount() {
    return negationCount;
  }
}
