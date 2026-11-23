// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAwaitExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/// @author Bas Leijdekkers
public final class GrAwaitExpressionImpl extends GrExpressionImpl implements GrAwaitExpression {
  public GrAwaitExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitAwaitExpression(this);
  }

  @Override
  public GrExpression @NotNull [] getAwaitedExpressions() {
    return findChildrenByClass(GrExpression.class);
  }

  @Override
  public @NotNull PsiElement getAwaitKeyword() {
    return findNotNullChildByType(GroovyTokenTypes.kAWAIT);
  }

  @Override
  public @Nullable PsiType getType() {
    GrExpression[] expressions = getAwaitedExpressions();
    if (expressions.length > 1) {
      return TypesUtil.createListType(this, TypesUtil.getJavaLangObject(this));
    }
    PsiType type = expressions[0].getType();
    if (type instanceof PsiClassType classType) {
      PsiType[] parameters = classType.getParameters();
      if (parameters.length == 1) {
        return parameters[0];
      }
    }
    return TypesUtil.getJavaLangObject(this);
  }
}
