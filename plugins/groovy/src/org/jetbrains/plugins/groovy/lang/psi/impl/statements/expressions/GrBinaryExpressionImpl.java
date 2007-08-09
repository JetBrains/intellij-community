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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author ilyas
 */
public abstract class GrBinaryExpressionImpl extends GrExpressionImpl implements GrBinaryExpression {

  public GrBinaryExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrExpression getLeftOperand() {
    PsiElement first = getFirstChild();
    if (first instanceof GrExpression) {
      return (GrExpression) first;
    } else {
      return null;
    }
  }

  public GrExpression getRightOperand() {
    PsiElement last = getLastChild();
    if (last instanceof GrExpression) {
      return (GrExpression) last;
    } else {
      return null;
    }
  }

  @Nullable
  public IElementType getOperationTokenType() {
    final PsiElement child = findChildByType(TokenSets.BINARY_OP_SET);
    if (child == null) return null;
    final ASTNode node = child.getNode();
    assert node != null;
    return node.getElementType();
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitBinaryExpression(this);
  }
}
