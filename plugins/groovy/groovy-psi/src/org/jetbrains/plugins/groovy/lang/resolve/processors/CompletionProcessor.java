// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.EnumSet;

import static org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.RESOLVE_KINDS_METHOD_PROPERTY;

/**
 * @author ven
 */
public class CompletionProcessor extends ResolverProcessorImpl {
  private CompletionProcessor(PsiElement place, final EnumSet<DeclarationKind> resolveTargets, final String name) {
    super(name, resolveTargets, place, PsiType.EMPTY_ARRAY);
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState substitutor) {
    if (element instanceof PsiMethod && ((PsiMethod)element).isConstructor()) {
      return true;
    }
    super.execute(element, substitutor);
    return true;
  }

  public static ResolverProcessor createPropertyCompletionProcessor(PsiElement place) {
    return new CompletionProcessor(place, RESOLVE_KINDS_METHOD_PROPERTY, null);
  }

  @Override
  @NotNull
  public GroovyResolveResult[] getCandidates() {
    if (!super.hasCandidates()) return GroovyResolveResult.EMPTY_ARRAY;
    return ResolveUtil.filterSameSignatureCandidates(getCandidatesInternal());
  }
}
