// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapTypeFromNamedArgs
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.TypeConstraint

class GrNamedArgumentsConverter : GrTypeConverter() {

  override fun isApplicableTo(position: Position): Boolean {
    return position == Position.METHOD_PARAMETER
  }

  override fun reduceTypeConstraint(leftType: PsiType, rightType: PsiType, position: Position, context: PsiElement): Collection<ConstraintFormula>? {
    if (!isPossibleToConvert(leftType, rightType, position, context)) return null
    return listOf(TypeConstraint(leftType, leftType, context))
  }

  private fun isPossibleToConvert(leftType: PsiType, rightType: PsiType, position: Position, context: PsiElement): Boolean {
    if (position != Position.METHOD_PARAMETER || rightType !is GrMapTypeFromNamedArgs) return false
    if (leftType !is PsiClassType) return false
    val rawTypeFqn = leftType.rawType().canonicalText
    if (rawTypeFqn != MAP_TYPE_NAME) return false
    leftType.parameters
    return true
  }


  override fun isConvertible(targetType: PsiType, actualType: PsiType, position: Position, context: GroovyPsiElement): ConversionResult {
    if (isPossibleToConvert(targetType, actualType, position, context)) return ConversionResult.OK
    else return ConversionResult.ERROR
  }

  companion object {
    private const val MAP_TYPE_NAME = "java.util.Map"
  }
}