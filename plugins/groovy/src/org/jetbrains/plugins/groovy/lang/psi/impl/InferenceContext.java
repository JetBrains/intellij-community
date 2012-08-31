/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import java.util.Map;

import static com.intellij.util.containers.ContainerUtil.newHashMap;

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
    public <T> T getCachedValue(@NotNull GroovyPsiElement element, final Computable<T> computable) {
      return CachedValuesManager.getManager(element.getProject()).getCachedValue(element, new CachedValueProvider<T>() {
        @Nullable
        @Override
        public Result<T> compute() {
          return Result.create(computable.compute(), PsiModificationTracker.MODIFICATION_COUNT);
        }
      });
    }

    @Override
    public <T extends PsiPolyVariantReference> GroovyResolveResult[] multiResolve(@NotNull T ref,
                                                                                  boolean incomplete,
                                                                                  ResolveCache.PolyVariantResolver<T> resolver) {
      ResolveResult[] results = ResolveCache.getInstance(ref.getElement().getProject()).resolveWithCaching(ref, resolver, true, incomplete);
      return results.length == 0 ? GroovyResolveResult.EMPTY_ARRAY : (GroovyResolveResult[])results;
    }
  };

  @Nullable
  PsiType getVariableType(@NotNull GrReferenceExpression ref);

  <T> T getCachedValue(@NotNull GroovyPsiElement element, Computable<T> computable);

  <T extends PsiPolyVariantReference> GroovyResolveResult[] multiResolve(@NotNull T ref, boolean incomplete, ResolveCache.PolyVariantResolver<T> resolver);

  class PartialContext implements InferenceContext {
    private final Map<String, PsiType> myTypes;
    private final Map<PsiElement, Map<Object, Object>> myCache = newHashMap();

    public PartialContext(Map<String, PsiType> types) {
      myTypes = types;
    }

    @Nullable
    @Override
    public PsiType getVariableType(@NotNull GrReferenceExpression ref) {
      return myTypes.get(ref.getReferenceName());
    }

    @Override
    public <T> T getCachedValue(@NotNull GroovyPsiElement element, Computable<T> computable) {
      return _getCachedValue(element, computable, computable.getClass());
    }

    private <T> T _getCachedValue(PsiElement element, Computable<T> computable, Object key) {
      Map<Object, Object> map = myCache.get(element);
      if (map == null) {
        myCache.put(element, map = newHashMap());
      }
      if (map.containsKey(key)) {
        //noinspection unchecked
        return (T)map.get(key);
      }

      T result = computable.compute();
      map.put(key, result);
      return result;
    }

    @Override
    public <T extends PsiPolyVariantReference> GroovyResolveResult[] multiResolve(@NotNull final T ref,
                                                                                  final boolean incomplete,
                                                                                  final ResolveCache.PolyVariantResolver<T> resolver) {
      return _getCachedValue(ref.getElement(), new Computable<GroovyResolveResult[]>() {
        @Override
        public GroovyResolveResult[] compute() {
          return (GroovyResolveResult[])resolver.resolve(ref, incomplete);
        }
      }, Pair.create(incomplete, resolver.getClass()));
    }
  }

}
