// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter

class ParametrizedClosure(val parameter: GrParameter) {
  val types: MutableList<PsiType> = ArrayList()
  val typeParameters: MutableList<PsiTypeParameter> = ArrayList()

  companion object {
    private const val CLOSURE_PARAMS = "ClosureParams"
    private const val FROM_STRING = "FromString"
    private const val SIMPLE_TYPE = "SimpleType"
    private const val ANNOTATION_PACKAGE = "groovy.transform.stc"
    private const val CLOSURE_PARAMS_FQ = "$ANNOTATION_PACKAGE.$CLOSURE_PARAMS"
    private const val FROM_STRING_FQ = "$ANNOTATION_PACKAGE.$FROM_STRING"
    private const val SIMPLE_TYPE_FQ = "$ANNOTATION_PACKAGE.$SIMPLE_TYPE"
    private fun typeHintFactory(parameterIndex: Int, genericIndex: Int) =
      { parameterList: PsiParameterList, pattern: PsiType ->
        val type = parameterList.parameters.getOrNull(parameterIndex)?.type.run {
          if (genericIndex == -1) {
            this
          }
          else {
            (this as? PsiClassType)?.typeArguments()?.take(genericIndex)?.lastOrNull() as? PsiType
          }
        }
        if (type == pattern) {
          createSingleParameterAnnotation(parameterIndex, genericIndex, parameterList.project)
        }
        else {
          null
        }
      }

    private val typeHintChooser: List<(PsiParameterList, PsiType) -> PsiAnnotation?> = run {
      cartesianProduct((0..2), (-1..2)).map { (paramIndex, genericIndex) -> typeHintFactory(paramIndex, genericIndex) }
    }


    private fun createSingleParameterAnnotation(parameterIndex: Int, genericIndex: Int, project: Project): PsiAnnotation {
      return GroovyPsiElementFactory.getInstance(project).createAnnotationFromText(
        "@$CLOSURE_PARAMS_FQ($ANNOTATION_PACKAGE.${evaluateParameterIndex(parameterIndex)}${evaluateGenericParameterIndex(genericIndex)})")
    }

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


  fun renderTypes(outerParameters: PsiParameterList) {
    if (typeParameters.size == 1) {
      val indexedAnnotation = typeHintChooser.mapNotNull { it(outerParameters, types.first()) }.firstOrNull()
      val resultAnnotation = indexedAnnotation ?: run {
        val signatureType = types.first()
        if (signatureType is PsiClassType && signatureType.resolve() is PsiTypeParameter) {
          null
        }
        else {
          createSimpleType(signatureType)
        }
      }
      if (resultAnnotation != null) {
        parameter.modifierList.addAnnotation(resultAnnotation.text.substring(1))
        return
      }
    }
    parameter.modifierList.addAnnotation(
      "$CLOSURE_PARAMS_FQ(value=$FROM_STRING_FQ, options=[\"${types.joinToString(",") {
        // todo: remove this
        tryToExtractUnqualifiedName(it.canonicalText)
      }}\"])")
  }

  private fun createSimpleType(type: PsiType): PsiAnnotation =
    GroovyPsiElementFactory.getInstance(parameter.project).createAnnotationFromText(
      "@$CLOSURE_PARAMS_FQ(value = $SIMPLE_TYPE_FQ, options = ['${type.canonicalText}'])")


  fun substituteTypes(resultSubstitutor: PsiSubstitutor) {
    val substitutedTypes = types.map { resultSubstitutor.substitute(it) }
    types.clear()
    types.addAll(substitutedTypes)
  }

}