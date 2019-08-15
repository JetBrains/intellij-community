// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure

import com.intellij.psi.PsiParameterList
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.getJavaLangObject
import org.jetbrains.plugins.groovy.intentions.style.inference.recursiveSubstitute
import org.jetbrains.plugins.groovy.intentions.style.inference.typeParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

class ParameterizedClosure(val parameter: GrParameter,
                           val typeParameters: List<PsiTypeParameter>,
                           val closureArguments: List<GrClosableBlock>,
                           newTypes: List<PsiType>) {
  val types: MutableList<PsiType> = mutableListOf(*newTypes.toTypedArray())
  val delegatesToCombiner: DelegatesToCombiner = DelegatesToCombiner()
  private val closureParamsCombiner: ClosureParamsCombiner = ClosureParamsCombiner()

  fun renderTypes(outerParameters: PsiParameterList,
                  substitutor: PsiSubstitutor,
                  typeParameters: Map<PsiTypeParameter, List<PsiTypeParameter>>): List<AnnotatingResult> {
    substituteTypes(substitutor, typeParameters)
    val closureParamsAnnotation = closureParamsCombiner.instantiateAnnotation(outerParameters, types)
    val delegatesToAnnotations = delegatesToCombiner.instantiateAnnotation(outerParameters)
    val primaryAnnotations = listOf(delegatesToAnnotations.first, closureParamsAnnotation).mapNotNull {
      it?.run { AnnotatingResult(parameter, it) }
    }
    return primaryAnnotations + delegatesToAnnotations.second
  }


  private fun substituteTypes(resultSubstitutor: PsiSubstitutor,
                              typeParametersDependencies: Map<PsiTypeParameter, List<PsiTypeParameter>>) {
    val substitutedTypes = types.map { parameterType ->
      val dependencies = typeParametersDependencies
        .filter { it.key.type() != parameterType }
        .flatMap { it.value.map(PsiTypeParameter::type) }.toSet()
      if (dependencies.contains(parameterType) || !resultSubstitutor.substitute(parameterType).equalsToText(parameterType.canonicalText)) {
        resultSubstitutor.recursiveSubstitute(parameterType)
      }
      else {
        resultSubstitutor.recursiveSubstitute(parameterType).typeParameter()?.extendsListTypes?.firstOrNull() ?: getJavaLangObject(
          parameter)
      }
    }
    types.clear()
    types.addAll(substitutedTypes)
  }


  override fun toString(): String =
    "${typeParameters.joinToString(prefix = "<", postfix = ">") { it.text }} Closure ${types.joinToString(prefix = "(", postfix = ")")}"

}