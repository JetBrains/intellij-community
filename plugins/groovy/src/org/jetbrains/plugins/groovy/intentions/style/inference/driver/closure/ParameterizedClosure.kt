// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import org.jetbrains.plugins.groovy.intentions.style.inference.cartesianProduct
import org.jetbrains.plugins.groovy.intentions.style.inference.isTypeParameter
import org.jetbrains.plugins.groovy.intentions.style.inference.typeParameter
import org.jetbrains.plugins.groovy.intentions.style.inference.unreachable
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

class ParameterizedClosure(val parameter: GrParameter) {
  val types: MutableList<PsiType> = ArrayList()
  val typeParameters: MutableList<PsiTypeParameter> = ArrayList()
  val closureArguments: MutableList<GrClosableBlock> = mutableListOf()

  companion object {
    private const val CLOSURE_PARAMS = "ClosureParams"
    const val FROM_STRING = "FromString"
    const val SIMPLE_TYPE = "SimpleType"
    const val FROM_ABSTRACT_TYPE_METHODS = "FromAbstractTypeMethods"
    const val MAP_ENTRY_OR_KEY_VALUE = "MapEntryOrKeyValue"
    private const val ANNOTATION_PACKAGE = "groovy.transform.stc"
    const val CLOSURE_PARAMS_FQ = "$ANNOTATION_PACKAGE.$CLOSURE_PARAMS"
    const val FROM_STRING_FQ = "$ANNOTATION_PACKAGE.$FROM_STRING"
    const val SIMPLE_TYPE_FQ = "$ANNOTATION_PACKAGE.$SIMPLE_TYPE"

    val availableHints = cartesianProduct((0..2), (-1..2)).map { (paramIndex, genericIndex) ->
      "${evaluateParameterIndex(paramIndex)}${evaluateGenericParameterIndex(genericIndex)}"
    }.toSet()

    private fun typeHintFactory(parameterIndex: Int, genericIndex: Int) =
      { parameterList: PsiParameterList, pattern: PsiType ->
        val type = parameterList.parameters.getOrNull(parameterIndex)?.type.run {
          if (genericIndex == -1) {
            this
          }
          else {
            (this as? PsiClassType)?.typeArguments()?.toList()?.elementAtOrNull(genericIndex) as? PsiType
          }
        }
        if (type == pattern) {
          createSingleParameterAnnotation(
            parameterIndex, genericIndex, parameterList.project)
        }
        else {
          null
        }
      }

    private val typeHintChooser: List<(PsiParameterList, PsiType) -> PsiAnnotation?> = run {
      cartesianProduct((0..2), (-1..2)).map { (paramIndex, genericIndex) ->
        typeHintFactory(paramIndex, genericIndex)
      }
    }

    private fun createSingleParameterAnnotation(parameterIndex: Int, genericIndex: Int, project: Project): PsiAnnotation {
      return GroovyPsiElementFactory.getInstance(project).createAnnotationFromText(
        createSingleParameterAnnotationText(parameterIndex, genericIndex))
    }

    private fun createSingleParameterAnnotationText(parameterIndex: Int, genericIndex: Int): String =
      "@$CLOSURE_PARAMS_FQ($ANNOTATION_PACKAGE.${evaluateParameterIndex(parameterIndex)}${evaluateGenericParameterIndex(genericIndex)})"


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

  override fun toString(): String =
    "${typeParameters.joinToString(prefix = "<", postfix = ">") { it.text }} Closure ${types.joinToString(prefix = "(", postfix = ")")}"


  fun renderTypes(outerParameters: PsiParameterList): String {
    if (typeParameters.size == 1) {
      val indexedAnnotation = typeHintChooser.mapNotNull { it(outerParameters, types.first()) }.firstOrNull()
      val resultAnnotation = indexedAnnotation ?: run {
        val signatureType = types.first()
        if (signatureType.isTypeParameter()) {
          null
        }
        else {
          createSimpleType(signatureType)
        }
      }
      if (resultAnnotation != null) {
        return resultAnnotation.text
      }
    }
    return """@$CLOSURE_PARAMS_FQ(value=$FROM_STRING_FQ, options=["${types.joinToString(",") { it.canonicalText }}"])"""
  }

  private fun createSimpleType(type: PsiType): PsiAnnotation =
    GroovyPsiElementFactory.getInstance(parameter.project).createAnnotationFromText(
      "@$CLOSURE_PARAMS_FQ(value = $SIMPLE_TYPE_FQ, options = ['${type.canonicalText}'])")


  fun substituteTypes(resultSubstitutor: PsiSubstitutor,
                      typeParametersDependencies: Map<PsiTypeParameter, List<PsiTypeParameter>>) {
    val javaLangObject = PsiType.getJavaLangObject(typeParameters.first().manager, typeParameters.first().resolveScope)
    val substitutedTypes = types.map { parameterType ->
      val dependencies = typeParametersDependencies
        .filter { it.key.type() != parameterType }
        .flatMap { it.value.map(PsiTypeParameter::type) }.toSet()
      if (dependencies.contains(parameterType) || !resultSubstitutor.substitute(parameterType).equalsToText(parameterType.canonicalText)) {
        resultSubstitutor.substitute(parameterType)
      }
      else {
        resultSubstitutor.substitute(parameterType).typeParameter()?.extendsListTypes?.firstOrNull() ?: javaLangObject
      }
    }
    types.clear()
    types.addAll(substitutedTypes)
  }

}