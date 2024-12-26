// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSynchronizedStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

public class GrSynchronizedStatementImpl extends GroovyPsiElementImpl implements GrSynchronizedStatement {

  public GrSynchronizedStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitSynchronizedStatement(this);
  }

  @Override
  public String toString() {
    return "Synchronized statement";
  }

  @Override
  public @Nullable GrExpression getMonitor() {
    return findExpressionChild(this);
  }

  @Override
  public @Nullable GrOpenBlock getBody() {
    return findChildByClass(GrOpenBlock.class);
  }

  @Override
  public @Nullable PsiElement getRParenth() {
    return findChildByType(GroovyTokenTypes.mRPAREN);
  }
}
