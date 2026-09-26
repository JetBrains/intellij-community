// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTuple;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTupleAssignmentExpression;

public class GrTupleAssignmentExpressionImpl extends GrExpressionImpl implements GrTupleAssignmentExpression {

  public GrTupleAssignmentExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public @Nullable PsiType getType() {
    GrExpression rValue = getRValue();
    return rValue == null ? null : rValue.getType();
  }

  @Override
  public @NotNull GrTuple getLValue() {
    return findNotNullChildByClass(GrTuple.class);
  }

  @Override
  public @Nullable GrExpression getRValue() {
    return findChildByClass(GrExpression.class);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitTupleAssignmentExpression(this);
  }

  @Override
  public String toString() {
    return "Tuple assignment";
  }
}
