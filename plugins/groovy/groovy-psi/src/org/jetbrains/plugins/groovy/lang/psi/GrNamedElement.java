// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public interface GrNamedElement extends PsiNameIdentifierOwner, GroovyPsiElement {

  @NotNull
  PsiElement getNameIdentifierGroovy();

  @Nullable
  @Override
  default PsiElement getIdentifyingElement() {
    return getNameIdentifierGroovy();
  }
}
