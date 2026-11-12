// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrDeferStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/// @author Bas Leijdekkers
public final class GrDeferStatementImpl extends GroovyPsiElementImpl implements GrDeferStatement {
  public GrDeferStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitDeferStatement(this);
  }

  @Override
  public String toString() {
    return "GrDeferStatement";
  }

  @Override
  public @Nullable GrExpression getValue() {
    return findExpressionChild(this);
  }

  @Override
  public @NotNull PsiElement getDeferKeyword() {
    return findNotNullChildByType(GroovyTokenTypes.kDEFER);
  }
}
