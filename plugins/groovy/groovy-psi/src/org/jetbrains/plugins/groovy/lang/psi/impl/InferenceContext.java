// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.ResolveCache.AbstractResolver;
import com.intellij.psi.impl.source.resolve.ResolveCache.PolyVariantResolver;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolver;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * @author peter
 */
public interface InferenceContext {
  InferenceContext TOP_CONTEXT = new TopInferenceContext();

  @Nullable
  PsiType getVariableType(@NotNull GrReferenceExpression ref);

  <T> T getCachedValue(@NotNull GroovyPsiElement element, @NotNull Computable<T> computable);

  @Nullable
  <T extends PsiReference, R>
  R resolveWithCaching(@NotNull T ref, @NotNull AbstractResolver<T, R> resolver, boolean incomplete);

  @NotNull
  default <T extends PsiPolyVariantReference>
  GroovyResolveResult[] multiResolve(@NotNull T ref, boolean incomplete, @NotNull PolyVariantResolver<T> resolver) {
    ResolveResult[] results = resolveWithCaching(ref, resolver, incomplete);
    return results == null || results.length == 0 ? GroovyResolveResult.EMPTY_ARRAY : (GroovyResolveResult[])results;
  }

  @NotNull
  default <T extends GroovyReference>
  Collection<? extends GroovyResolveResult> resolve(@NotNull T ref, boolean incomplete, @NotNull GroovyResolver<T> resolver) {
    Collection<? extends GroovyResolveResult> results = resolveWithCaching(ref, resolver, incomplete);
    return results == null ? Collections.emptyList() : results;
  }

  @Nullable
  <T extends GroovyPsiElement> PsiType getExpressionType(@NotNull T element, @NotNull Function<T, PsiType> calculator);

  class TopInferenceContext implements InferenceContext {
    @Nullable
    @Override
    public PsiType getVariableType(@NotNull GrReferenceExpression ref) {
      return TypeInferenceHelper.getInferredType(ref);
    }

    @Override
    public <T> T getCachedValue(@NotNull GroovyPsiElement element, @NotNull final Computable<T> computable) {
      CachedValuesManager manager = CachedValuesManager.getManager(element.getProject());
      Key<CachedValue<T>> key = manager.getKeyForClass(computable.getClass());
      return manager.getCachedValue(element, key, () -> CachedValueProvider.Result
        .create(computable.compute(), PsiModificationTracker.MODIFICATION_COUNT), false);
    }

    @Nullable
    @Override
    public <T extends PsiReference, R> R resolveWithCaching(@NotNull T ref, @NotNull AbstractResolver<T, R> resolver, boolean incomplete) {
      return ResolveCache.getInstance(ref.getElement().getProject()).resolveWithCaching(ref, resolver, true, incomplete);
    }

    @Nullable
    @Override
    public <T extends GroovyPsiElement> PsiType getExpressionType(@NotNull T element, @NotNull Function<T, PsiType> calculator) {
      return GroovyPsiManager.getInstance(element.getProject()).getType(element, calculator);
    }
  }

  class PartialContext extends TopInferenceContext {
    private final Map<String, PsiType> myTypes;
    private final InferenceContext myInheritedContext;

    public PartialContext(@NotNull Map<String, PsiType> types, @NotNull InferenceContext inheritedContext) {
      myTypes = types;
      myInheritedContext = inheritedContext;
    }

    @Nullable
    @Override
    public PsiType getVariableType(@NotNull GrReferenceExpression ref) {
      String referenceName = ref.getReferenceName();
      if (myTypes.containsKey(referenceName)) return myTypes.get(referenceName);
      try {
        myTypes.put(referenceName, null);
        return myInheritedContext.getVariableType(ref);
      } finally {
        myTypes.remove(referenceName);
      }
    }
  }
}
