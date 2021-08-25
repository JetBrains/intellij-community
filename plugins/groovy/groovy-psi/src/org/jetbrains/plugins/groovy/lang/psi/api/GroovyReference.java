// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;

/**
 * Same as {@link PsiPolyVariantReference} but returns {@link GroovyResolveResult}.
 */
public interface GroovyReference extends PsiPolyVariantReference {

  GroovyReference[] EMPTY_ARRAY = new GroovyReference[0];

  @Nullable
  @Override
  default PsiElement resolve() {
    return advancedResolve().getElement();
  }

  @NotNull
  default GroovyResolveResult advancedResolve() {
    Collection<? extends GroovyResolveResult> results = resolve(false);
    if (results.size() == 1) {
      return getFirstItem(results);
    }
    else {
      return EmptyGroovyResolveResult.INSTANCE;
    }
  }

  /**
   * Either this or {@link #resolve(boolean)} must be implemented.
   */
  @Override
  default GroovyResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    return resolve(incompleteCode).toArray(GroovyResolveResult.EMPTY_ARRAY);
  }

  /**
   * Either this or {@link #multiResolve(boolean)} must be implemented.
   *
   * @param incomplete if true, the code in the context of which the reference is being resolved is considered incomplete,
   *                   and the method may return additional invalid results.
   * @return read-only collection of results
   */
  @NotNull
  default Collection<? extends GroovyResolveResult> resolve(boolean incomplete) {
    return new SmartList<>(multiResolve(incomplete));
  }
}
