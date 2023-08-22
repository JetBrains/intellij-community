// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.psi.PsiReference
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.impl.source.resolve.ResolveCache.AbstractResolver
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper
import java.util.function.Function

class TopInferenceContext : InferenceContext {

  override fun getVariableType(ref: GrReferenceExpression): PsiType? = TypeInferenceHelper.getInferredType(ref)

  override fun <E : GroovyPsiElement, T> getCachedValue(element: E, computation: Function<in E, out T>): T {
    return CachedValuesManager.getProjectPsiDependentCache(element, computation)
  }

  override fun <T : PsiReference, R> resolveWithCaching(ref: T, resolver: AbstractResolver<T, Array<R>>, incomplete: Boolean): Array<R>? {
    return ResolveCache.getInstance(ref.element.project).resolveWithCaching(ref, resolver, true, incomplete)
  }

  override fun <T : GroovyPsiElement> getExpressionType(element: T, calculator: Function<in T, out PsiType>): PsiType? {
    return CachedValuesManager.getProjectPsiDependentCache(element, calculator)
  }
}
