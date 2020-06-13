// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.dsl.psi;

import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

/**
 * @author ilyas
 */
public final class PsiCategoryUtil {

  @Nullable
  public static PsiClass getClassType(PsiType type, PsiElement place) {
    if (type instanceof PsiClassType) {
      PsiClassType classType = (PsiClassType)type;
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
