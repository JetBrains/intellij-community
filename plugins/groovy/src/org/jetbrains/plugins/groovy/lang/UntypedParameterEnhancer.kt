// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.intentions.style.inference.runInferenceProcess
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrVariableEnhancer

class UntypedParameterEnhancer : GrVariableEnhancer() {

  companion object {
    private val entranceMark = Key<Unit>("untypedParameterEnhancerMark")

    fun produceTypedMethod(method: GrMethod, scope: GlobalSearchScope, postProcessing: (GrMethod) -> Unit = {}): GrMethod {
      method.putUserData(entranceMark, Unit)
      val typedMethod = runInferenceProcess(method, scope)
      postProcessing(typedMethod)
      method.putUserData(entranceMark, null)
      return typedMethod
    }
  }

  override fun getVariableType(variable: GrVariable): PsiType? {
    if (variable is GrParameter && variable.typeElement == null && variable.containingFile.viewProvider.isPhysical) {
      val method = variable.parentOfType<GrMethod>() ?: return null
      if (entranceMark.isIn(method)) {
        return null
      }
      else {
        val typedMethod = produceTypedMethod(method, GlobalSearchScope.fileScope(method.containingFile))
        return typedMethod.parameters.find { it.name == variable.name }?.type
      }
    }
    return null
  }
}
