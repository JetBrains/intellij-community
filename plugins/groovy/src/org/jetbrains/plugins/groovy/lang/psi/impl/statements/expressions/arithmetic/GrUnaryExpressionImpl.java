/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;

/**
 * @author ilyas
 */
public class GrUnaryExpressionImpl extends GrExpressionImpl implements GrUnaryExpression {
  private static final String PATTERN_FQ_NAME = "java.util.regex.Pattern";

  public GrUnaryExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Unary expression";
  }

  public PsiType getType() {
    IElementType opToken = getOperationTokenType();
    GrExpression operand = getOperand();
    if (operand == null) return null;
    PsiType opType = operand.getType();
    if (opToken == GroovyTokenTypes.mINC || opToken == GroovyTokenTypes.mDEC) {
      return TypesUtil.getTypeForIncOrDecExpression(this);
    }
    
    if (opToken == GroovyTokenTypes.mBNOT) {
      if (opType.equalsToText("java.lang.String")) {
        return getTypeByFQName(PATTERN_FQ_NAME);
      }
    }

    return opType;
  }

  public IElementType getOperationTokenType() {
    PsiElement opElement = findChildByType(GroovyTokenTypes.UNARY_OP_SET);
    assert opElement != null;
    ASTNode node = opElement.getNode();
    assert node != null;
    return node.getElementType();
  }

  public GrExpression getOperand() {
    return findChildByClass(GrExpression.class);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitUnaryExpression(this);
  }
}