// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.signatures;

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;

public class GrDelegatingClosureParameter implements GrClosureParameter {
  private final GrClosureParameter myDelegate;

  public GrDelegatingClosureParameter(GrClosureParameter delegate) {
    myDelegate = delegate;
  }

  @Override
  public @Nullable PsiType getType() {
    return myDelegate.getType();
  }

  @Override
  public boolean isOptional() {
    return myDelegate.isOptional();
  }

  @Override
  public @Nullable GrExpression getDefaultInitializer() {
    return myDelegate.getDefaultInitializer();
  }

  @Override
  public boolean isValid() {
    return myDelegate.isValid();
  }

  @Override
  public @Nullable String getName() {
    return myDelegate.getName();
  }
}
