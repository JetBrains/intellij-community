// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.api;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCandidate;

public interface GroovyMethodResult extends GroovyResolveResult {

  @NotNull
  @Override
  PsiMethod getElement();

  default @NotNull PsiSubstitutor getPartialSubstitutor() {
    return getSubstitutor();
  }

  @NotNull
  @Override
  PsiSubstitutor getSubstitutor();

  default @NotNull Applicability getApplicability() {
    return isApplicable() ? Applicability.applicable : Applicability.inapplicable;
  }

  default @Nullable GroovyMethodCandidate getCandidate() {
    return null;
  }
}
