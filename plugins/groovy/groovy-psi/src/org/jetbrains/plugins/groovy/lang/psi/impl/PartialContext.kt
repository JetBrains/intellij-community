// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.psi.PsiReference
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.ResolveCache.AbstractResolver
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.createDescriptor
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType
import java.util.function.Function

internal class PartialContext(private val types: Map<VariableDescriptor, DFAType>) : InferenceContext {
  private val cache = HashMap<Any, Any>()

  private fun <T> doGetCachedValue(key: Any, computable: () -> T): T {
    // computeIfAbsent cannot be used here - ConcurrentModificationException
    @Suppress("UNCHECKED_CAST")
    return (cache as MutableMap<Any, T>).getOrPut(key) {
      computable()
    }
  }

  override fun getVariableType(ref: GrReferenceExpression): PsiType? {
    val descriptor = ref.createDescriptor()
    if (types.containsKey(descriptor)) {
      return types[descriptor]?.resultType
    }
    else {
      return null
    }
  }

  override fun <E : GroovyPsiElement, T> getCachedValue(element: E, computation: Function<in E, out T>): T {
    return doGetCachedValue(Pair(element, computation.javaClass)) {
      computation.apply(element)
    }
  }

  override fun <T : PsiReference, R> resolveWithCaching(ref: T, resolver: AbstractResolver<T, Array<R>>, incomplete: Boolean): Array<R>? {
    return doGetCachedValue(Pair(ref, incomplete)) {
      resolver.resolve(ref, incomplete)
    }
  }

  override fun <T : GroovyPsiElement> getExpressionType(element: T, calculator: Function<in T, out PsiType>): PsiType {
    return doGetCachedValue(Pair(element, "type")) {
      calculator.apply(element)
    }
  }
}
