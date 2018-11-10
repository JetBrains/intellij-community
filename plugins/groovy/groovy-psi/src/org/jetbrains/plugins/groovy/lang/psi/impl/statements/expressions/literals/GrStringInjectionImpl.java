// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author Maxim.Medvedev
 */
public class GrStringInjectionImpl extends GroovyPsiElementImpl implements GrStringInjection {
  public GrStringInjectionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  @Nullable
  public GrExpression getExpression() {
    final GrExpression expression = findExpressionChild(this);
    return expression instanceof GrClosableBlock ? null : expression;
  }

  @Override
  @Nullable
  public GrClosableBlock getClosableBlock() {
    return findChildByClass(GrClosableBlock.class);
  }

  @Override
  public String toString() {
    return "GString injection";
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitGStringInjection(this);
  }
}
