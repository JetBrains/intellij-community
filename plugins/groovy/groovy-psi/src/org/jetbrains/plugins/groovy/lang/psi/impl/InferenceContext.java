/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;

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

    @NotNull
    @Override
    public <T extends PsiPolyVariantReference> GroovyResolveResult[] multiResolve(@NotNull T ref,
                                                                                  boolean incomplete,
                                                                                  ResolveCache.PolyVariantResolver<T> resolver) {
      ResolveResult[] results = ResolveCache.getInstance(ref.getElement().getProject()).resolveWithCaching(ref, resolver, true, incomplete);
      return results.length == 0 ? GroovyResolveResult.EMPTY_ARRAY : (GroovyResolveResult[])results;
    }

    @Nullable
    @Override
    public <T extends GroovyPsiElement> PsiType getExpressionType(@NotNull T element, @NotNull Function<T, PsiType> calculator) {
      return GroovyPsiManager.getInstance(element.getProject()).getType(element, calculator);
    }
  };

  @Nullable
  PsiType getVariableType(@NotNull GrReferenceExpression ref);

  @NotNull
  <T extends PsiPolyVariantReference> GroovyResolveResult[] multiResolve(@NotNull T ref, boolean incomplete, ResolveCache.PolyVariantResolver<T> resolver);

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

    @NotNull
    @Override
    public <T extends PsiPolyVariantReference> GroovyResolveResult[] multiResolve(@NotNull final T ref,
                                                                                  final boolean incomplete,
                                                                                  @NotNull final ResolveCache.PolyVariantResolver<T> resolver) {
      return _getCachedValue(ref.getElement(), new Computable<GroovyResolveResult[]>() {
        @Override
        public GroovyResolveResult[] compute() {
          return (GroovyResolveResult[])resolver.resolve(ref, incomplete);
        }
      }, Pair.create(incomplete, resolver.getClass()));
    }

    @Nullable
    @Override
    public <T extends GroovyPsiElement> PsiType getExpressionType(@NotNull final T element, @NotNull final Function<T, PsiType> calculator) {
      return _getCachedValue(element, new Computable<PsiType>() {
        @Override
        public PsiType compute() {
          return calculator.fun(element);
        }
      }, "type");
    }
  }

}
