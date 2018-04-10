/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ilyas
 */
public interface GrBinaryExpression extends GrOperatorExpression {

  /**
   * @return left operand of binary expression
   */
  @NotNull
  GrExpression getLeftOperand();

  /**
   * @return right operand of binary expression
   */
  @Nullable
  GrExpression getRightOperand();

  @NotNull
  PsiElement getOperationToken();

  @NotNull
  IElementType getOperationTokenType();
}
