// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/// @author Bas Leijdekkers
public interface GrDeferStatement extends GrStatement {

  /// @return the value of this defer statement
  @Nullable
  GrExpression getValue();

  /// Returns the `defer` keyword
  @NotNull
  PsiElement getDeferKeyword();
}
