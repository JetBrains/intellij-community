// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.api;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.SyntheticElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface GroovyProperty extends PsiNamedElement, SyntheticElement {

  @Override
  @NotNull
  String getName();

  @Nullable
  PsiType getPropertyType();

  @Override
  default PsiElement setName(@NotNull String name) {
    throw new IncorrectOperationException();
  }
}
