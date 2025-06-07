// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.checkers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

final class AliasedAnnotationHolder {

  static @NotNull PsiElement findCodeElement(@NotNull PsiElement elt, @NotNull GrAnnotation alias, @NotNull GrCodeReferenceElement aliasReference) {
    if (PsiTreeUtil.isAncestor(alias, elt, true)) {
      return elt;
    }
    else {
      return aliasReference;
    }
  }

}
