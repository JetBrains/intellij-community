// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.impl.source.resolve.ResolveCache.AbstractResolver
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.Function
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper

class TopInferenceContext : InferenceContext {

  override fun getVariableType(ref: GrReferenceExpression): PsiType? = TypeInferenceHelper.getInferredType(ref)

  override fun <T> getCachedValue(element: GroovyPsiElement, computable: Computable<T>): T {
    val manager = CachedValuesManager.getManager(element.project)
    val key = manager.getKeyForClass<T>(computable.javaClass)
    return manager.getCachedValue(element, key, {
      Result.create(computable.compute(), PsiModificationTracker.MODIFICATION_COUNT)
    }, false)
  }

  override fun <T : PsiReference, R> resolveWithCaching(ref: T, resolver: AbstractResolver<T, R>, incomplete: Boolean): R? {
    return ResolveCache.getInstance(ref.element.project).resolveWithCaching<T, R>(ref, resolver, true, incomplete)
  }

  override fun <T : GroovyPsiElement> getExpressionType(element: T, calculator: Function<in T, out PsiType>): PsiType? {
    return GroovyPsiManager.getInstance(element.project).getType(element, calculator)
  }
}
