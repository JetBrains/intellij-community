// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.parentOfType
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
      val scope = getFileScope(method) ?: return null
      return computeInferredMethod(method, scope)
    }

    private fun computeInferredMethod(method: GrMethod, scope: GlobalSearchScope): InferenceResult? =
      RecursionManager.doPreventingRecursion(method, true) {
        CachedValuesManager.getCachedValue(method) {
          val typedMethod = runInferenceProcess(method, scope)
          val typeParameterSubstitutor = createVirtualToActualSubstitutor(typedMethod, method)
          CachedValueProvider.Result(InferenceResult(typedMethod, typeParameterSubstitutor), method)
        }
      }

    private fun getFileScope(method: GrMethod): GlobalSearchScope? {
      val originalMethod = getOriginalMethod(method)
      return with(originalMethod.containingFile?.virtualFile) {
        if (this == null) return null else GlobalSearchScope.fileScope(originalMethod.project, this)
      }
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