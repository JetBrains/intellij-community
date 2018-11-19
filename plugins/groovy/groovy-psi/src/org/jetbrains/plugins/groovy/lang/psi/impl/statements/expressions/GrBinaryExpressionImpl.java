// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.AtomicNullableLazyValue;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrOperatorExpressionImpl;
import org.jetbrains.plugins.groovy.lang.resolve.references.GrOperatorReference;

import static org.jetbrains.plugins.groovy.lang.psi.GroovyTokenSets.BINARY_OPERATORS;
import static org.jetbrains.plugins.groovy.lang.resolve.references.GrOperatorReference.hasOperatorReference;

/**
 * @author ilyas
 */
public abstract class GrBinaryExpressionImpl extends GrOperatorExpressionImpl implements GrBinaryExpression {

  private final NullableLazyValue<GroovyReference> myReference = AtomicNullableLazyValue.createValue(
    () -> hasOperatorReference(this) ? new GrOperatorReference(this) : null
  );

  public GrBinaryExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Nullable
  @Override
  public GroovyReference getReference() {
    return myReference.getValue();
  }

  @Override
  @Nullable
  public PsiType getRightType() {
    final GrExpression rightOperand = getRightOperand();
    return rightOperand == null ? null : rightOperand.getType();
  }

  @Nullable
  @Override
  public PsiType getLeftType() {
    return getLeftOperand().getType();
  }

  @Override
  @NotNull
  public GrExpression getLeftOperand() {
    return findNotNullChildByClass(GrExpression.class);
  }

  @Override
  @Nullable
  public GrExpression getRightOperand() {
    final PsiElement last = getLastChild();
    return last instanceof GrExpression ? (GrExpression)last : null;
  }

  @Override
  @NotNull
  public PsiElement getOperationToken() {
    return findNotNullChildByType(BINARY_OPERATORS);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitBinaryExpression(this);
  }
}
