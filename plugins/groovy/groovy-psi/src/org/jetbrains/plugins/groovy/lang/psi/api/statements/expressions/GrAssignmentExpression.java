/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents simple or operator assignment expression.
 * <pre>
 * a = b
 * c += d
 * </pre>
 *
 * @see GrTupleAssignmentExpression
 */
public interface GrAssignmentExpression extends GrOperatorExpression {

  @NotNull
  PsiElement getOperationToken();

  @NotNull
  IElementType getOperationTokenType();

  @NotNull
  GrExpression getLValue();

  @Nullable
  GrExpression getRValue();

  boolean isOperatorAssignment();
}
