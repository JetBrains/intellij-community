// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure

import com.intellij.psi.PsiParameterList
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.groovy.intentions.style.inference.recursiveSubstitute
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter

class ParameterizedClosure(val parameter: GrParameter,
                           val typeParameters: List<PsiTypeParameter>,
                           val closureArguments: List<GrClosableBlock>,
                           newTypes: List<PsiType>) {
  val types: MutableList<PsiType> = newTypes.toMutableList()
  val delegatesToCombiner: DelegatesToCombiner = DelegatesToCombiner()
  private val closureParamsCombiner: ClosureParamsCombiner = ClosureParamsCombiner()

  fun renderTypes(outerParameters: PsiParameterList,
                  substitutor: PsiSubstitutor): List<AnnotatingResult> {
    substituteTypes(substitutor)
    val closureParamsAnnotation = closureParamsCombiner.instantiateAnnotation(outerParameters, types)
    val delegatesToAnnotations = delegatesToCombiner.instantiateAnnotation(outerParameters)
    val primaryAnnotations = listOf(delegatesToAnnotations.first, closureParamsAnnotation).mapNotNull {
      it?.run { AnnotatingResult(parameter, it) }
    }
    return primaryAnnotations + delegatesToAnnotations.second
  }


  private fun substituteTypes(resultSubstitutor: PsiSubstitutor) {
    val substitutedTypes = types.map { parameterType ->
      resultSubstitutor.recursiveSubstitute(parameterType)
    }
    types.clear()
    types.addAll(substitutedTypes)
  }


  override fun toString(): String =
    "${typeParameters.joinToString(prefix = "<", postfix = ">") { it.text }} Closure ${types.joinToString(prefix = "(", postfix = ")")}"

}