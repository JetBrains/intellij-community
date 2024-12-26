// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCallReference;

public interface GrUnaryExpression extends GrExpression {

  @NotNull
  @Override
  GroovyMethodCallReference getReference();

  /**
   * @return type of this expression with regard to whether this expression is prefix or postfix
   */
  @Override
  default @Nullable PsiType getType() {
    return GrExpression.super.getType();
  }

  /**
   * @return type of operator call performed by this expression independently of whether this expression is prefix or postfix
   */
  @Nullable
  PsiType getOperationType();

  @NotNull
  IElementType getOperationTokenType();

  @NotNull
  PsiElement getOperationToken();

  @Nullable
  GrExpression getOperand();

  boolean isPostfix();
}
