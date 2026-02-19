// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure.getBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeAugmenter

class MethodParameterAugmenter : TypeAugmenter() {

  companion object {

    const val GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE = "groovy.collect.method.calls.for.inference"

    internal fun createInferenceResult(method: GrMethod): InferenceResult? {
      if (!Registry.`is`(GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE, false)) {
        return null
      }
      if (method is LightElement) {
        return null
      }
      return computeInferredMethod(method)
    }

    private fun computeInferredMethod(method: GrMethod): InferenceResult? =
      CachedValuesManager.getCachedValue(method) {
        RecursionManager.doPreventingRecursion(method, true) {
          val options = SignatureInferenceOptions(true, ClosureIgnoringInferenceContext(method.manager))
          val typedMethod = runInferenceProcess(method, options)
          val typeParameterSubstitutor = createVirtualToActualSubstitutor(typedMethod, method)
          CachedValueProvider.Result(InferenceResult(typedMethod, typeParameterSubstitutor), method)
        }
      }

  }

  data class InferenceResult(val virtualMethod: GrMethod?, val typeParameterSubstitutor: PsiSubstitutor)


  override fun inferType(variable: GrVariable): PsiType? {
    if (variable !is GrParameter || variable.typeElement != null || getBlock(variable) != null) {
      return null
    }
    val method = variable.parentOfType<GrMethod>()?.takeIf { it.parameters.contains(variable) && it !is LightElement } ?: return null
    val inferenceResult = createInferenceResult(method)
    val parameterIndex = method.parameterList.getParameterNumber(variable)
    return inferenceResult?.virtualMethod?.parameters?.getOrNull(parameterIndex)
      ?.takeIf { it.typeElementGroovy != null }?.type
      ?.let { inferenceResult.typeParameterSubstitutor.substitute(it) }
  }
}