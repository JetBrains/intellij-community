// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import org.jetbrains.plugins.groovy.intentions.style.inference.cartesianProduct
import org.jetbrains.plugins.groovy.intentions.style.inference.isTypeParameter
import org.jetbrains.plugins.groovy.intentions.style.inference.unreachable
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.*

class ClosureParamsCombiner {


  companion object {
    private const val ANNOTATION_PACKAGE = "groovy.transform.stc"

    val availableHints = cartesianProduct((0..2), (-1..2)).map { (paramIndex, genericIndex) ->
      "$ANNOTATION_PACKAGE.${evaluateParameterIndex(paramIndex)}${evaluateGenericParameterIndex(genericIndex)}"
    }.toSet()

    private fun typeHintFactory(parameterIndex: Int, genericIndex: Int) =
      { parameterList: PsiParameterList, pattern: PsiType ->
        val desiredParameter = parameterList.parameters.getOrNull(parameterIndex)?.type
        val desiredGenericParameter = (desiredParameter as? PsiClassType)?.typeArguments()?.elementAtOrNull(genericIndex) as? PsiType
        val type = if (genericIndex == -1) desiredParameter else desiredGenericParameter
        if (type == pattern) {
          createSingleParameterAnnotation(parameterIndex, genericIndex, parameterList.project)
        }
        else {
          null
        }
      }

    private val typeHintChoosers: List<(PsiParameterList, PsiType) -> PsiAnnotation?> = run {
      cartesianProduct((0..2), (-1..2)).map { (paramIndex, genericIndex) -> typeHintFactory(paramIndex, genericIndex) }
    }

    private fun createSingleParameterAnnotation(parameterIndex: Int, genericIndex: Int, project: Project): PsiAnnotation {
      return GroovyPsiElementFactory.getInstance(project).createAnnotationFromText(
        createSingleParameterAnnotationText(parameterIndex, genericIndex))
    }

    private fun createSingleParameterAnnotationText(parameterIndex: Int, genericIndex: Int): String =
      "@$GROOVY_TRANSFORM_STC_CLOSURE_PARAMS($ANNOTATION_PACKAGE.${evaluateParameterIndex(parameterIndex)}${evaluateGenericParameterIndex(
        genericIndex)})"


    private fun evaluateParameterIndex(index: Int): String =
      when (index) {
        0 -> "First"
        1 -> "Second"
        2 -> "Third"
        else -> unreachable()
      } + "Param"


    private fun evaluateGenericParameterIndex(genericIndex: Int): String {
      return when (genericIndex) {
        -1 -> ""
        else -> "." + when (genericIndex) {
          0 -> "First"
          1 -> "Second"
          2 -> "Third"
          else -> unreachable()
        } + "GenericType"
      }
    }

  }


  private fun createSimpleType(types: Iterable<PsiType>, context: PsiElement): PsiAnnotation {
    val typesRepresentation = types.joinToString(", ") { "'${it.canonicalText}'" }
    return GroovyPsiElementFactory.getInstance(context.project).createAnnotationFromText(
      "@$GROOVY_TRANSFORM_STC_CLOSURE_PARAMS(value = $GROOVY_TRANSFORM_STC_SIMPLE_TYPE, options = [$typesRepresentation]) ")

  }


  fun instantiateAnnotation(outerParameters: PsiParameterList,
                            signatureTypes: List<PsiType>): String? {
    if (signatureTypes.all { it.equalsToText("?") || it == PsiType.NULL }) {
      return null
    }
    if (signatureTypes.size == 1) {
      val patternType = signatureTypes.single()
      val singleParameterAnnotation = typeHintChoosers
        .mapNotNull { hintChooser -> hintChooser.invoke(outerParameters, patternType) }
        .firstOrNull()
      if (singleParameterAnnotation != null) {
        return singleParameterAnnotation.text
      }
    }
    if (signatureTypes.all { type -> !type.anyComponent { it.isTypeParameter() } }) {
      return createSimpleType(signatureTypes, outerParameters).text
    }
    val signatureTypesRepresentation = signatureTypes.joinToString(",") { it.canonicalText }
    return """@$GROOVY_TRANSFORM_STC_CLOSURE_PARAMS(value=$GROOVY_TRANSFORM_STC_FROM_STRING, options=["${signatureTypesRepresentation}"])"""
  }


}