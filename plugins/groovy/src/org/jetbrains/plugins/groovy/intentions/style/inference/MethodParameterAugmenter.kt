// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter.Companion.VisitState.NOT_VISITED
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter.Companion.VisitState.VISITED_MANY
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeAugmenter

class MethodParameterAugmenter : TypeAugmenter() {

  companion object {

    const val GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE = "groovy.collect.method.calls.for.inference"

    private val methodRegistry: ThreadLocal<MutableMap<GrMethod, VisitState>> =
      ThreadLocal.withInitial { mutableMapOf<GrMethod, VisitState>() }

    internal fun createInferenceResult(method: GrMethod): InferenceResult? {
      if (!Registry.`is`(GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE, false)) {
        return null
      }
      val originalMethod = getOriginalMethod(method)
      val scope = with(originalMethod.containingFile?.virtualFile) {
        if (this == null) return null else GlobalSearchScope.fileScope(originalMethod.project, this)
      }
      val involvedMethods = methodRegistry.get()
      if (involvedMethods.getOrDefault(method, NOT_VISITED) == VISITED_MANY) {
        return InferenceResult(method, PsiSubstitutor.EMPTY)
      }
      else {
        involvedMethods[method] = involvedMethods.getOrDefault(method, NOT_VISITED).nextState()
        try {
          return computeInferredMethod(method, scope)
        }
        finally {
          involvedMethods[method] = involvedMethods[method]!!.prevState()
        }
      }
    }

    private fun computeInferredMethod(method: GrMethod, scope: GlobalSearchScope): InferenceResult =
      CachedValuesManager.getCachedValue(method) {
        val typedMethod = runInferenceProcess(method, scope)
        val typeParameterSubstitutor = createVirtualToActualSubstitutor(typedMethod, method)
        CachedValueProvider.Result(InferenceResult(typedMethod, typeParameterSubstitutor), method)
      }

    private enum class VisitState {
      NOT_VISITED {
        override fun nextState(): VisitState = VISITED_ONCE
        override fun prevState(): VisitState = NOT_VISITED
      },
      VISITED_ONCE {
        override fun nextState(): VisitState = VISITED_MANY
        override fun prevState(): VisitState = NOT_VISITED
      },
      VISITED_MANY {
        override fun nextState(): VisitState = VISITED_MANY
        override fun prevState(): VisitState = VISITED_ONCE
      };

      abstract fun nextState(): VisitState
      abstract fun prevState(): VisitState
    }
  }

  data class InferenceResult(val virtualMethod: GrMethod?, val typeParameterSubstitutor: PsiSubstitutor)


  override fun inferType(variable: GrVariable): PsiType? {
    if (variable !is GrParameter || variable.typeElement != null) {
      return null
    }
    val method = variable.parentOfType<GrMethod>()?.takeIf { it.parameters.contains(variable) } ?: return null
    val inferenceResult = createInferenceResult(method)
    val parameterIndex = method.parameterList.getParameterNumber(variable)
    return inferenceResult?.virtualMethod?.parameters?.getOrNull(parameterIndex)
      ?.takeIf { it.typeElementGroovy != null }?.type
      ?.let { inferenceResult.typeParameterSubstitutor.substitute(it) }
  }
}