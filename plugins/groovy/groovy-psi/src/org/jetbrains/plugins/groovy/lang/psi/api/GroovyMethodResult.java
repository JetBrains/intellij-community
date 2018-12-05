// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  default PsiSubstitutor getPartialSubstitutor() {
    return getSubstitutor();
  }

  @NotNull
  @Override
  PsiSubstitutor getSubstitutor();

  @NotNull
  default Applicability getApplicability() {
    return isApplicable() ? Applicability.applicable : Applicability.inapplicable;
  }

  @Nullable
  default GroovyMethodCandidate getCandidate() {
    return null;
  }
}
