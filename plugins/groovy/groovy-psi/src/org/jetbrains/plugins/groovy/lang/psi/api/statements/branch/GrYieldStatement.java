// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.api.statements.branch;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * Represents {@code yield} statement in Groovy (introduced as a part of switch expressions in Groovy 4.0.0)
 */
public interface GrYieldStatement extends GrStatement {

  /**
   * Returns the value that is ought to be yielded from a branch of a switch expression.
   */
  @Nullable
  GrExpression getYieldedValue();

  /**
   * Returns the keyword {@code yield}
   */
  @NotNull
  PsiElement getYieldKeyword();
}
