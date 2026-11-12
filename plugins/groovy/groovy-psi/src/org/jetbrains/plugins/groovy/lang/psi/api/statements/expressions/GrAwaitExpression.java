// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/// @author Bas Leijdekkers
public interface GrAwaitExpression extends GrExpression {

  /// @return the awaited expression. The array may be empty for incomplete code.
  GrExpression @NotNull [] getAwaitedExpressions();

  /// @return the `await` keyword
  @NotNull
  PsiElement getAwaitKeyword();
}
