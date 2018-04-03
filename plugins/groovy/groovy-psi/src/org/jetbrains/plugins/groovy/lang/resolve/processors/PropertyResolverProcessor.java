// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable;

import java.util.List;

import static org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.RESOLVE_KINDS_PROPERTY;

/**
 * @author ven
 */
public class PropertyResolverProcessor extends ResolverProcessorImpl implements DynamicMembersHint {

  public PropertyResolverProcessor(String name, PsiElement place) {
    super(name, RESOLVE_KINDS_PROPERTY, place, PsiType.EMPTY_ARRAY);
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    return super.execute(element, state) || element instanceof PsiField;
  }

  @NotNull
  @Override
  public GroovyResolveResult[] getCandidates() {
    //do not have more than one correct result. And if it exists it is the last
    final List<GroovyResolveResult> candidates = getCandidatesInternal();
    final int size = candidates.size();
    if (size == 0) return GroovyResolveResult.EMPTY_ARRAY;

    GroovyResolveResult last = candidates.get(size - 1);
    if (last.getElement() instanceof GrBindingVariable && size > 1) {
      last = candidates.get(size - 2);
    }
    if (isCorrectLocalVarOrParam(last)) {
      return new GroovyResolveResult[]{last};
    }
    for (GroovyResolveResult candidate : candidates) {
      if (candidate.isStaticsOK()) {
        return new GroovyResolveResult[]{candidate};
      }
    }
    return candidates.toArray(GroovyResolveResult.EMPTY_ARRAY);
  }

  private static boolean isCorrectLocalVarOrParam(GroovyResolveResult last) {
    return !(last.getElement() instanceof PsiField) &&
           last.isAccessible() &&
           last.isStaticsOK() &&
           last.getCurrentFileResolveContext() == null;
  }

  @Override
  public <T> T getHint(@NotNull Key<T> hintKey) {
    if (hintKey == DynamicMembersHint.KEY) {
      //noinspection unchecked
      return (T)this;
    }
    return super.getHint(hintKey);
  }

  @Override
  public boolean shouldProcessProperties() {
    return true;
  }
}
