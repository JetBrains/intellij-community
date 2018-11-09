// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.types;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.resolve.references.GrSafeCastReference;

public class GrSafeCastExpressionImpl extends GrExpressionImpl implements GrSafeCastExpression {

  private final GroovyReference myReference = new GrSafeCastReference(this);

  public GrSafeCastExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  public GroovyReference getReference() {
    return myReference;
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitSafeCastExpression(this);
  }

  @Override
  public String toString() {
    return "Safe cast expression";
  }

  @Override
  @Nullable
  public GrTypeElement getCastTypeElement() {
    return findChildByClass(GrTypeElement.class);
  }

  @Override
  @NotNull
  public GrExpression getOperand() {
    return findNotNullChildByClass(GrExpression.class);
  }

  @NotNull
  @Override
  public PsiElement getOperationToken() {
    return findNotNullChildByType(GroovyTokenTypes.kAS);
  }
}
