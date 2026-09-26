// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure

import com.intellij.psi.PsiParameterList
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter

class ParameterizedClosure(val parameter: GrParameter,
                           val typeParameters: List<PsiTypeParameter>,
                           val closureArguments: List<GrClosableBlock>,
                           val types: List<PsiType>,
                           val delegatesToCombiner: DelegatesToCombiner = DelegatesToCombiner()) {
  private val closureParamsCombiner: ClosureParamsCombiner = ClosureParamsCombiner()

  fun renderTypes(outerParameters: PsiParameterList): List<AnnotatingResult> {
    val closureParamsAnnotation = closureParamsCombiner.instantiateAnnotation(outerParameters, types)
    val (delegatesToAnnotation, parameterAnnotatingResult) = delegatesToCombiner.instantiateAnnotation(outerParameters)
    val primaryAnnotatingResults = listOfNotNull(delegatesToAnnotation, closureParamsAnnotation)
      .map { annotationText -> AnnotatingResult(parameter, annotationText) }
    if (parameterAnnotatingResult != null) {
      return primaryAnnotatingResults + parameterAnnotatingResult
    }
    else {
      return primaryAnnotatingResults
    }
  }


  override fun toString(): String =
    "${typeParameters.joinToString(prefix = "<", postfix = ">") { it.text }} Closure ${types.joinToString(prefix = "(", postfix = ")")}"

}