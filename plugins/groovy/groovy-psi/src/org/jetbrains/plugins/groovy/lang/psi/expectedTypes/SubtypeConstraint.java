// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.expectedTypes;

import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

/**
 * @author ven
 */
public final class SubtypeConstraint extends TypeConstraint {
  private final PsiType myDefaultType;

  SubtypeConstraint(@NotNull PsiType type, @NotNull PsiType defaultType) {
    super(type);
    myDefaultType = defaultType;
  }

  @Override
  public boolean satisfied(PsiType type, @NotNull PsiElement context){
    return TypesUtil.isAssignableByMethodCallConversion(getType(), type, context);
  }

  @Override
  @NotNull
  public PsiType getDefaultType() {
    return myDefaultType;
  }

  public static SubtypeConstraint create(@NotNull PsiType type) {
    return new SubtypeConstraint(type, type);
  }

  public static SubtypeConstraint create(String fqName, PsiElement context) {
    PsiClassType type = TypesUtil.createType(fqName, context);
    return new SubtypeConstraint(type, type);
  }
}
