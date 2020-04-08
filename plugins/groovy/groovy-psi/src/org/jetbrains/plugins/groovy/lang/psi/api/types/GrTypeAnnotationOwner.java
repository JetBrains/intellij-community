// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Nullable
  @Override
  default PsiAnnotation findAnnotation(@NotNull String qualifiedName) {
    for (GrAnnotation annotation : getAnnotations()) {
      if (annotation.hasQualifiedName(qualifiedName)) {
        return annotation;
      }
    }
    return null;
  }

  @NotNull
  @Override
  default PsiAnnotation addAnnotation(@NotNull String qualifiedName) {
    throw new IncorrectOperationException();
  }
}
