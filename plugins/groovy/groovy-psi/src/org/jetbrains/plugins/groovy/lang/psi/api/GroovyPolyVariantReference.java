// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Same as {@link PsiPolyVariantReference} but returns {@link GroovyResolveResult}.
 */
public interface GroovyPolyVariantReference extends PsiPolyVariantReference {

  GroovyPolyVariantReference[] EMPTY_ARRAY = new GroovyPolyVariantReference[0];

  @Nullable
  @Override
  default PsiElement resolve() {
    return advancedResolve().getElement();
  }

  @NotNull
  default GroovyResolveResult advancedResolve() {
    GroovyResolveResult[] results = multiResolve(false);
    return results.length == 1 ? results[0] : EmptyGroovyResolveResult.INSTANCE;
  }

  @NotNull
  @Override
  GroovyResolveResult[] multiResolve(boolean incompleteCode);
}
