// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression;

/// @author Bas Leijdekkers
public interface GrAsyncExpression extends GrExpression {

  /// @return the closure or lambda body of this `async` expression
  GrFunctionalExpression getBody();

  /// @return the `async` keyword
  @NotNull
  PsiElement getAsyncKeyword();
}
