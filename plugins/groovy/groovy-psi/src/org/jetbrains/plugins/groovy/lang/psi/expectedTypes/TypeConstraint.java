// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.expectedTypes;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

public abstract class TypeConstraint {
  public static final TypeConstraint[] EMPTY_ARRAY = new TypeConstraint[0];

  protected final PsiType myType;

  public abstract boolean satisfied(PsiType type, @NotNull PsiElement context);

  public abstract @NotNull PsiType getDefaultType();

  protected TypeConstraint(@NotNull PsiType type) {
    myType = type;
  }

  public @NotNull PsiType getType() {
    return myType;
  }
}
