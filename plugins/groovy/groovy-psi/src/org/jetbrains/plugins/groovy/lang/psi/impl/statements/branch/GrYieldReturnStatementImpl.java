// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.branch;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrYieldReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/// @author Bas Leijdekkers
public final class GrYieldReturnStatementImpl extends GroovyPsiElementImpl implements GrYieldReturnStatement {
  public GrYieldReturnStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitYieldReturnStatement(this);
  }

  @Override
  public @Nullable GrExpression getValue() {
    return findExpressionChild(this);
  }

  @Override
  public @NotNull PsiElement getYieldKeyword() {
    return findNotNullChildByType(GroovyTokenTypes.kYIELD);
  }

  @Override
  public @NotNull PsiElement getReturnKeyword() {
    return findNotNullChildByType(GroovyTokenTypes.kRETURN);
  }

  @Override
  public String toString() {
    return "GrYieldReturnStatement";
  }
}
