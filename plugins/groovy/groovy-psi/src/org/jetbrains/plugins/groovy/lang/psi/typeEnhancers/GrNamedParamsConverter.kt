// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers

import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapTypeFromNamedArgs
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.TypeConstraint
import org.jetbrains.plugins.groovy.transformations.impl.namedVariant.collectNamedParams

class GrNamedParamsConverter : GrTypeConverter() {

  override fun isApplicableTo(position: Position): Boolean {
    return position == Position.METHOD_PARAMETER
  }

  override fun reduceTypeConstraint(leftType: PsiType, rightType: PsiType, position: Position, context: PsiElement): Collection<ConstraintFormula>? =
    when (val result = createConversion(leftType, rightType, context)) {
      is Result.Success -> listOf(result.typeConstraint)
      else -> null
    }

  private fun createConversion(targetType: PsiType, actualType: PsiType, context: PsiElement): Result {
    if (actualType !is GrMapTypeFromNamedArgs || targetType !is PsiClassType) return Result.NotNamedParams
    if (!PsiTypesUtil.classNameEquals(targetType, CommonClassNames.JAVA_UTIL_MAP)) return Result.NotNamedParams

    if (context !is GrMethodCallExpression) return Result.NotNamedParams
    val method = context.resolveMethod() ?: return Result.NotNamedParams

    val namedParamList = method.parameterList.parameters.mapNotNull { parameter ->
      val namedParamList = collectNamedParams(parameter)
      namedParamList.ifEmpty { null }
    }.singleOrNull() ?: return Result.NotNamedParams


    for (namedParam in namedParamList) {
      if (namedParam.required && namedParam.name !in actualType.stringKeys) {
        return Result.Error
      }
    }

    if (!targetType.isRaw) {
      val upperBound = targetType.parameters.last()

      for (key in actualType.stringKeys) {
        val expressionType = actualType.getTypeByStringKey(key)
        if (expressionType == null || !upperBound.isAssignableFrom(expressionType)) {
          return Result.Error
        }
      }
    }

    return Result.Success(TypeConstraint(targetType, targetType, context))
  }


  override fun isConvertible(targetType: PsiType, actualType: PsiType, position: Position, context: GroovyPsiElement): ConversionResult? =
    when (createConversion(targetType, actualType, context)) {
      Result.NotNamedParams -> null
      Result.Error -> ConversionResult.ERROR
      else -> ConversionResult.OK
    }

  private abstract class Result {
    object NotNamedParams : Result()

    data class Success(val typeConstraint: TypeConstraint) : Result()

    object Error : Result()
  }
}