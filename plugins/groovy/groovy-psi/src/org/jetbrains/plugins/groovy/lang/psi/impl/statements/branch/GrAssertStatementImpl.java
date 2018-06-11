// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.branch;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrAssertStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author ilyas
 */
public class GrAssertStatementImpl extends GroovyPsiElementImpl implements GrAssertStatement {
  public GrAssertStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitAssertStatement(this);
  }

  public String toString() {
    return "ASSERT statement";
  }

  @Override
  @Nullable
  public GrExpression getAssertion() {
    return findExpressionChild(this);
  }

  @Override
  public GrExpression getErrorMessage() {
    GrExpression[] exprs = findChildrenByClass(GrExpression.class);
    return exprs.length >= 2 ? exprs[1] : null;
  }
}
