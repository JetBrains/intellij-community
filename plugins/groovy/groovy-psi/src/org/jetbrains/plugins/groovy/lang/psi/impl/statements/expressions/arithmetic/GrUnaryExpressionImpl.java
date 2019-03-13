// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.resolve.references.GrUnaryOperatorReference;
import org.jetbrains.plugins.groovy.lang.typing.GrTypeCalculator;

import java.util.Objects;

public class GrUnaryExpressionImpl extends GrExpressionImpl implements GrUnaryExpression {

  private final GroovyReference myReference = new GrUnaryOperatorReference(this);

  public GrUnaryExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  public GroovyReference getReference() {
    return myReference;
  }

  @Override
  public String toString() {
    return "Unary expression";
  }

  @Nullable
  @Override
  public PsiType getType() {
    if (isPostfix()) {
      return Objects.requireNonNull(getOperand()).getType();
    }
    else {
      return getOperationType();
    }
  }

  @Nullable
  @Override
  public PsiType getOperationType() {
    return TypeInferenceHelper.getCurrentContext().getExpressionType(this, GrTypeCalculator::getTypeFromCalculators);
  }

  @Override
  @NotNull
  public IElementType getOperationTokenType() {
    PsiElement opElement = getOperationToken();
    ASTNode node = opElement.getNode();
    assert node != null;
    return node.getElementType();
  }

  @Override
  @NotNull
  public PsiElement getOperationToken() {
    PsiElement opElement = findChildByType(TokenSets.UNARY_OP_SET);
    assert opElement != null;
    return opElement;
  }

  @Override
  public GrExpression getOperand() {
    return findExpressionChild(this);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitUnaryExpression(this);
  }

  @Override
  public boolean isPostfix() {
    return getFirstChild() instanceof GrExpression;
  }
}
