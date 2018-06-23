// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.ResolveCache.AbstractResolver;
import com.intellij.psi.impl.source.resolve.ResolveCache.PolyVariantResolver;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
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
  InferenceContext TOP_CONTEXT = new InferenceContext() {
    @Nullable
    @Override
    public PsiType getVariableType(@NotNull GrReferenceExpression ref) {
      return TypeInferenceHelper.getInferredType(ref);
    }

    @Override
    public <T> T getCachedValue(@NotNull GroovyPsiElement element, @NotNull final Computable<T> computable) {
      CachedValuesManager manager = CachedValuesManager.getManager(element.getProject());
      Key<CachedValue<T>> key = manager.getKeyForClass(computable.getClass());
      return manager.getCachedValue(element, key, () -> CachedValueProvider.Result.create(computable.compute(), PsiModificationTracker.MODIFICATION_COUNT), false);
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
  };

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

  class PartialContext implements InferenceContext {
    private final Map<String, PsiType> myTypes;
    private final Map<PsiElement, Map<Object, Object>> myCache = ContainerUtil.newHashMap();

    public PartialContext(@NotNull Map<String, PsiType> types) {
      myTypes = types;
    }

    @Nullable
    @Override
    public PsiType getVariableType(@NotNull GrReferenceExpression ref) {
      return myTypes.get(ref.getReferenceName());
    }

    @Override
    public <T> T getCachedValue(@NotNull GroovyPsiElement element, @NotNull Computable<T> computable) {
      return _getCachedValue(element, computable, computable.getClass());
    }

    private <T> T _getCachedValue(@Nullable PsiElement element, @NotNull Computable<T> computable, @NotNull Object key) {
      Map<Object, Object> map = myCache.get(element);
      if (map == null) {
        myCache.put(element, map = ContainerUtil.newHashMap());
      }
      if (map.containsKey(key)) {
        //noinspection unchecked
        return (T)map.get(key);
      }

      T result = computable.compute();
      map.put(key, result);
      return result;
    }

    @Nullable
    @Override
    public <T extends PsiReference, R> R resolveWithCaching(@NotNull T ref, @NotNull AbstractResolver<T, R> resolver, boolean incomplete) {
      return _getCachedValue(ref.getElement(), () -> {
        final Pair<T, Boolean> key = Pair.create(ref, incomplete);
        return RecursionManager.doPreventingRecursion(key, true, () -> resolver.resolve(ref, incomplete));
      }, Pair.create(incomplete, resolver.getClass()));
    }

    @Nullable
    @Override
    public <T extends GroovyPsiElement> PsiType getExpressionType(@NotNull final T element, @NotNull final Function<T, PsiType> calculator) {
      return _getCachedValue(element, () -> calculator.fun(element), "type");
    }
  }
}
