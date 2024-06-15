// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers

import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
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

  override fun reduceTypeConstraint(leftType: PsiType, rightType: PsiType, position: Position, context: PsiElement): Collection<ConstraintFormula>? {
    if (!isNamedArgument(leftType, rightType, context) || !isPossibleToConvert(leftType, rightType, context)) return null
    return listOf(TypeConstraint(leftType, leftType, context))
  }

  private fun isPossibleToConvert(leftType: PsiType, rightType: PsiType, context: PsiElement): Boolean {
    return false
  }


  private fun isNamedArgument(leftType: PsiType, rightType: PsiType, context: PsiElement): Boolean {
    if (rightType !is GrMapTypeFromNamedArgs || leftType !is PsiClassType) return false
    val rawTypeFqn = leftType.rawType().canonicalText
    if (rawTypeFqn != CommonClassNames.JAVA_UTIL_MAP) return false

    if (context !is GrMethodCallExpression) return false

    val method = context.resolveMethod() ?: return false



    return method.parameterList.parameters.any { parameter -> collectNamedParams(parameter).isNotEmpty() }
  }


  override fun isConvertible(targetType: PsiType, actualType: PsiType, position: Position, context: GroovyPsiElement): ConversionResult? {
    if (!isNamedArgument(targetType, actualType, context)) return null
    return if (isPossibleToConvert(targetType, actualType, context)) ConversionResult.OK else ConversionResult.ERROR
  }
}