// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.ResolveCache.AbstractResolver
import com.intellij.util.Function
import gnu.trove.THashMap
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

class PartialContext(private val types: Map<String, PsiType>) : InferenceContext {

  private val myCache = THashMap<Any, Any>()

  private fun <T> doGetCachedValue(key: Any, computable: () -> T): T {
    @Suppress("UNCHECKED_CAST")
    val cache = myCache as MutableMap<Any, T>
    return cache.computeIfAbsent(key) {
      computable()
    }
  }

  override fun getVariableType(ref: GrReferenceExpression): PsiType? {
    val referenceName = ref.referenceName
    if (types.containsKey(referenceName)) {
      return types[referenceName]
    }
    else {
      return null
    }
  }

  override fun <T> getCachedValue(element: GroovyPsiElement, computable: Computable<T>): T {
    return doGetCachedValue(Pair(element, computable.javaClass)) {
      computable.compute()
    }
  }

  override fun <T : PsiReference, R> resolveWithCaching(ref: T, resolver: AbstractResolver<T, R>, incomplete: Boolean): R? {
    return doGetCachedValue(Pair(ref, incomplete)) {
      resolver.resolve(ref, incomplete)
    }
  }

  override fun <T : GroovyPsiElement> getExpressionType(element: T, calculator: Function<in T, out PsiType>): PsiType? {
    return doGetCachedValue(Pair(element, "type")) {
      calculator.`fun`(element)
    }
  }
}
