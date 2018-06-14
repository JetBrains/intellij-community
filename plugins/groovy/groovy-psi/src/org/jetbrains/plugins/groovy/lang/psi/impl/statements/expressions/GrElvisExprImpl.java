// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrElvisExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author ilyas
 */
public class GrElvisExprImpl extends GrConditionalExprImpl implements GrElvisExpression {

  public GrElvisExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Elvis expression";
  }

  @Override
  public GrExpression getThenBranch() {
    return getCondition();
  }

  @Override
  public GrExpression getElseBranch() {
    GrExpression[] exprs = findChildrenByClass(GrExpression.class);
    if (exprs.length > 1) {
      return exprs[1];
    }
    return null;
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitElvisExpression(this);
  }
}
