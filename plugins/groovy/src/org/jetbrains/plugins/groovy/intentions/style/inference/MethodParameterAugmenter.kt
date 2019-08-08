// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeAugmenter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException

class MethodParameterAugmenter : TypeAugmenter() {

  companion object {
    private val methodProcessingMark = Key<Unit>("methodParameterAugmenterMark")
    private val reentrancyMark: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    private val cacheMap: MutableMap<GrMethod, AsyncPromise<GrMethod>> = ConcurrentHashMap()

    private inline fun calculateTypedMethod(untypedMethod: GrMethod,
                                            scope: GlobalSearchScope,
                                            postProcessing: (GrMethod) -> Unit): GrMethod {
      try {
        reentrancyMark.set(true)
        return runInferenceProcess(untypedMethod, scope).apply(postProcessing)
      }
      finally {
        reentrancyMark.set(false)
      }
    }


    internal fun produceTypedMethod(untypedMethod: GrMethod, scope: GlobalSearchScope, postProcessing: (GrMethod) -> Unit = {}): GrMethod? {
      val (promise, needCalculations) = synchronized(untypedMethod) {
        if (methodProcessingMark.isIn(untypedMethod)) {
          cacheMap[untypedMethod]!! to false
        }
        else {
          untypedMethod.putUserData(methodProcessingMark, Unit)
          AsyncPromise<GrMethod>().apply { cacheMap[untypedMethod] = this } to true
        }
      }

      val typedMethod = if (needCalculations) {
        calculateTypedMethod(untypedMethod, scope, postProcessing).apply {
          promise.setResult(this)
        }
      }
      else {
        try {
          promise.blockingGet(100)
        }
        catch (e: TimeoutException) {
          null
        }
      }

      synchronized(untypedMethod) {
        untypedMethod.putUserData(methodProcessingMark, null)
      }
      return typedMethod
    }
  }


  override fun inferType(variable: GrVariable): PsiType? {
    if (variable is GrParameter && variable.typeElement == null && !reentrancyMark.get()) {
      val method = variable.parentOfType<GrMethod>() ?: return null
      val parameterIndex = method.parameterList.getParameterNumber(variable)
      val typedMethod = produceTypedMethod(method, GlobalSearchScope.fileScope(method.containingFile))
      return typedMethod?.parameters?.getOrNull(parameterIndex)?.takeIf { it.typeElementGroovy != null }?.type
    }
    return null
  }
}