// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrFinallyClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

public class GrFinallyClauseImpl extends GroovyPsiElementImpl implements GrFinallyClause {
  public GrFinallyClauseImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitFinallyClause(this);
  }

  @Override
  public String toString() {
    return "Finally clause";
  }

  @Override
  public @Nullable GrOpenBlock getBody() {
    return findChildByClass(GrOpenBlock.class);
  }

}
