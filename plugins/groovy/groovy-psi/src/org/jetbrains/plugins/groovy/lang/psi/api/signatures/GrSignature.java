// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api.signatures;

import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;

public interface GrSignature {

  boolean isValid();

  @NotNull
  PsiSubstitutor getSubstitutor();

  GrClosureParameter @NotNull [] getParameters();

  int getParameterCount();

  boolean isVarargs();

  @Nullable
  PsiType getReturnType();

  boolean isCurried();
}
