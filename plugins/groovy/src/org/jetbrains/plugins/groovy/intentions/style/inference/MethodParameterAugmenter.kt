// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

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
    private val reentrancyMark: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    internal inline fun produceTypedMethod(untypedMethod: GrMethod,
                                           scope: GlobalSearchScope,
                                           postProcessing: (GrMethod) -> Unit = {}): GrMethod? {
      try {
        reentrancyMark.set(true)
        return runInferenceProcess(untypedMethod, scope).apply(postProcessing)
      }
      finally {
        reentrancyMark.set(false)
      }
    }
  }


  override fun inferType(variable: GrVariable): PsiType? {
    if (variable is GrParameter && variable.typeElement == null && !reentrancyMark.get()) {
      val method = variable.parentOfType<GrMethod>() ?: return null
      val typedMethod = CachedValuesManager.getCachedValue(method) {
        CachedValueProvider.Result(produceTypedMethod(method, GlobalSearchScope.fileScope(method.containingFile)), method)
      }
      val parameterIndex = method.parameterList.getParameterNumber(variable)
      return typedMethod?.parameters?.getOrNull(parameterIndex)?.takeIf { it.typeElementGroovy != null }?.type
    }
    return null
  }
}