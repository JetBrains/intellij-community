// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.api.statements.branch;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/// @author Bas Leijdekkers
public interface GrYieldReturnStatement extends GrStatement {

  /// @return the value of this `yield return` statement
  @Nullable GrExpression getValue();

  /// @return the `yield` keyword
  @NotNull PsiElement getYieldKeyword();

  /// @return the `return` keyword
  @NotNull PsiElement getReturnKeyword();
}
