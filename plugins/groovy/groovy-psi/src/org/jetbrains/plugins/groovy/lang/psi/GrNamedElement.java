// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface GrNamedElement extends PsiNameIdentifierOwner, GroovyPsiElement {

  @NotNull
  PsiElement getNameIdentifierGroovy();

  @Override
  default @Nullable PsiElement getIdentifyingElement() {
    return getNameIdentifierGroovy();
  }
}
