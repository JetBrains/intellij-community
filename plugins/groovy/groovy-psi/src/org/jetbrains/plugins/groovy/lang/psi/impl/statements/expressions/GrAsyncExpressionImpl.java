// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAsyncExpression;

/// @author Bas Leijdekkers
public final class GrAsyncExpressionImpl extends GrExpressionImpl implements GrAsyncExpression {
  public GrAsyncExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitAsyncExpression(this);
  }

  @Override
  public GrFunctionalExpression getBody() {
    return findChildByClass(GrFunctionalExpression.class);
  }

  @Override
  public @NotNull PsiElement getAsyncKeyword() {
    return findNotNullChildByType(GroovyTokenTypes.kASYNC);
  }

  @Override
  public @NotNull PsiType getType() {
    return TypesUtil.createTypeByFQClassName("groovy.concurrent.Awaitable", this);
  }

  @Override
  public String toString() {
    return "GrAsyncExpression";
  }
}
