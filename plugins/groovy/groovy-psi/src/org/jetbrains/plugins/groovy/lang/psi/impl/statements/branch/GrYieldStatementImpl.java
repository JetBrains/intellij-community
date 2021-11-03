// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.branch;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrYieldStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

public class GrYieldStatementImpl extends GroovyPsiElementImpl implements GrYieldStatement {
  public GrYieldStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitYieldStatement(this);
  }

  @Override
  public String toString() {
    return "YIELD statement";
  }

  @Override
  @Nullable
  public GrExpression getYieldedValue() {
    return findExpressionChild(this);
  }

  @NotNull
  @Override
  public PsiElement getYieldKeyword() {
    return findNotNullChildByType(GroovyTokenTypes.kYIELD);
  }
}
