// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrYieldReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAsyncExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

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
    return CachedValuesManager.getCachedValue(this, () -> {
      return new CachedValueProvider.Result<>(doGetType(), PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  private @NotNull PsiClassType doGetType() {
    GrFunctionalExpression body = getBody();
    if (body != null) {
      PsiType returnType = body.getReturnType();
      if (PsiTypes.voidType().equals(returnType)) {
        for (GrYieldReturnStatement statement : PsiTreeUtil.findChildrenOfType(body, GrYieldReturnStatement.class)) {
          GrExpression value = statement.getValue();
          if (value != null) {
            return TypesUtil.createGenericType("java.lang.Iterable", this, value.getType());
          }
        }
      }
      if (PsiTypes.nullType().equals(returnType)) {
        return TypesUtil.createGenericType("groovy.concurrent.Awaitable", this, TypesUtil.createTypeByFQClassName("java.lang.Void", this));
      }
      if (returnType != null) {
        return TypesUtil.createGenericType("groovy.concurrent.Awaitable", this, returnType);
      }
    }
    return TypesUtil.createTypeByFQClassName("groovy.concurrent.Awaitable<?>", this);
  }

  @Override
  public String toString() {
    return "GrAsyncExpression";
  }
}
