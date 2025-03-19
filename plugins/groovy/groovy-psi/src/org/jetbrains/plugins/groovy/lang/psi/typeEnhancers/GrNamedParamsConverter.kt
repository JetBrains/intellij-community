// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers

import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.util.PsiTypesUtil
import groovy.transform.NamedParam
import groovy.transform.NamedParams
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapTypeFromNamedArgs
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.TypeConstraint
import org.jetbrains.plugins.groovy.transformations.impl.namedVariant.collectNamedParams

/**
 * Named arguments in groovy are stored as [LinkedHashMap] in [GrMapTypeFromNamedArgs] whereas
 * Groovy [NamedParams] annotation usually stored as [Map].
 * The [GrNamedParamsConverter] ensures that:
 * 1) [GrMapTypeFromNamedArgs] are being converted to the exactly one [NamedParams] annotation.
 * 2) All required named params are present in [GrMapTypeFromNamedArgs] (see [NamedParam.required]).
 * 3) All types of named arguments are convertible to the value type of dictionary corresponding to the [NamedParams] annotation.
 */
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
    if (actualType !is GrMapTypeFromNamedArgs || targetType !is PsiClassType || !PsiTypesUtil.classNameEquals(targetType, CommonClassNames.JAVA_UTIL_MAP) || context !is GrMethodCallExpression) return Result.NotNamedParamsError

    val method = context.resolveMethod() ?: return Result.NotNamedParamsError

    val namedParamList = method.parameterList.parameters.mapNotNull { parameter ->
      val namedParamList = collectNamedParams(parameter)
      namedParamList.ifEmpty { null }
    }.singleOrNull() ?: return Result.NotNamedParamsError


    for (namedParam in namedParamList) {
      if (namedParam.required && namedParam.name !in actualType.stringKeys) {
        return Result.IncompatibleTypesError
      }
    }

    if (!targetType.isRaw) {
      val upperBound = targetType.parameters.last()

      for (key in actualType.stringKeys) {
        val expressionType = actualType.getTypeByStringKey(key)
        if (expressionType == null || !upperBound.isAssignableFrom(expressionType)) {
          return Result.IncompatibleTypesError
        }
      }
    }

    return Result.Success(TypeConstraint(targetType, targetType, context))
  }


  override fun isConvertible(targetType: PsiType, actualType: PsiType, position: Position, context: GroovyPsiElement): ConversionResult? =
    when (createConversion(targetType, actualType, context)) {
      Result.NotNamedParamsError -> null
      Result.IncompatibleTypesError -> ConversionResult.ERROR
      else -> ConversionResult.OK
    }

  private abstract class Result {
    object NotNamedParamsError : Result()

    object IncompatibleTypesError : Result()

    data class Success(val typeConstraint: TypeConstraint) : Result()
  }
}