/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
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

  @Nullable
  @Override
  public PsiType getType() {
    GrExpression rValue = getRValue();
    return rValue == null ? null : rValue.getType();
  }

  @NotNull
  @Override
  public GrTuple getLValue() {
    return findNotNullChildByClass(GrTuple.class);
  }

  @Nullable
  @Override
  public GrExpression getRValue() {
    return findChildByClass(GrExpression.class);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitTupleAssignmentExpression(this);
  }

  @Override
  public String toString() {
    return "Tuple assignment";
  }
}
