// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.resolve.ResolveCache.AbstractResolver;
import com.intellij.psi.impl.source.resolve.ResolveCache.PolyVariantResolver;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolver;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

/**
 * @author peter
 */
public interface InferenceContext {

  InferenceContext TOP_CONTEXT = new TopInferenceContext();

  @Nullable
  PsiType getVariableType(@NotNull GrReferenceExpression ref);

  <E extends @NotNull GroovyPsiElement, T>
  T getCachedValue(E element, @NotNull Function<@NotNull ? super E, ? extends T> computation);

  <T extends PsiReference, R>
  R[] resolveWithCaching(@NotNull T ref, @NotNull AbstractResolver<T, R[]> resolver, boolean incomplete);

  default <T extends PsiPolyVariantReference>
  GroovyResolveResult @NotNull [] multiResolve(@NotNull T ref, boolean incomplete, @NotNull PolyVariantResolver<T> resolver) {
    ResolveResult[] results = resolveWithCaching(ref, resolver, incomplete);
    return results == null || results.length == 0 ? GroovyResolveResult.EMPTY_ARRAY : (GroovyResolveResult[])results;
  }

  @NotNull
  default <T extends GroovyReference>
  Collection<? extends GroovyResolveResult> resolve(@NotNull T ref, boolean incomplete, @NotNull GroovyResolver<T> resolver) {
    GroovyResolveResult[] results = resolveWithCaching(ref, resolver, incomplete);
    return results == null ? Collections.emptyList() : new SmartList<>(results);
  }

  <T extends GroovyPsiElement>
  @Nullable PsiType getExpressionType(@NotNull T element, @NotNull Function<? super T, ? extends PsiType> calculator);
}
