// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.expectedTypes;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

public  class SupertypeConstraint extends TypeConstraint {
  private final PsiType myDefaultType;

  protected SupertypeConstraint(@NotNull PsiType type, @NotNull PsiType defaultType) {
    super(type);
    myDefaultType = defaultType;
  }

  @Override
  public boolean satisfied(PsiType type, @NotNull PsiElement context){
    return TypesUtil.isAssignableByMethodCallConversion(type, getType(), context);
  }

  @Override
  public @NotNull PsiType getDefaultType() {
    return myDefaultType;
  }

  public static SupertypeConstraint create(@NotNull PsiType type, @NotNull PsiType defaultType) {
    return new SupertypeConstraint(type, defaultType);
  }

  public static SupertypeConstraint create(@NotNull PsiType type) {
    return new SupertypeConstraint(type, type);
  }

}
