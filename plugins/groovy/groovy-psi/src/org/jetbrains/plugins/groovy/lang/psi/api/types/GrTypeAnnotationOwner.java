// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.api.types;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationOwner;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;

public interface GrTypeAnnotationOwner extends PsiAnnotationOwner {
  @Override
  GrAnnotation @NotNull [] getAnnotations();

  @Override
  default GrAnnotation @NotNull [] getApplicableAnnotations() {
    return getAnnotations();
  }

  @Override
  default @Nullable PsiAnnotation findAnnotation(@NotNull String qualifiedName) {
    for (GrAnnotation annotation : getAnnotations()) {
      if (annotation.hasQualifiedName(qualifiedName)) {
        return annotation;
      }
    }
    return null;
  }

  @Override
  default @NotNull PsiAnnotation addAnnotation(@NotNull String qualifiedName) {
    throw new IncorrectOperationException();
  }
}
