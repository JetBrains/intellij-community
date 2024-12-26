// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.dsl.psi;

import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

public final class PsiCategoryUtil {

  public static @Nullable PsiClass getClassType(PsiType type, PsiElement place) {
    if (type instanceof PsiClassType classType) {
      return classType.resolve();
    } else if (type instanceof PsiPrimitiveType) {
      final PsiClassType boxed = ((PsiPrimitiveType)type).getBoxedType(place);
      if (boxed != null) return boxed.resolve();
      else return null;
    } else {
      return null;
    }
  }
}
