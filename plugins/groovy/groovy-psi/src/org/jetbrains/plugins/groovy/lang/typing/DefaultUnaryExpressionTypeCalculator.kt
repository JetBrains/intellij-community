// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.isNumericType

class DefaultUnaryExpressionTypeCalculator : GrTypeCalculator<GrUnaryExpression> {

  override fun getType(expression: GrUnaryExpression): PsiType? {
    return getTypeFromOperator(expression)
           ?: getNumericType(expression)
  }

  private fun getTypeFromOperator(expression: GrUnaryExpression): PsiType? {
    val reference = expression.reference
    val result = reference.advancedResolve()
    return getTypeFromResult(result, reference.arguments, expression)
  }

  private fun getNumericType(expression: GrUnaryExpression): PsiType? {
    val type = expression.operand?.type
    return if (isNumericType(type)) type else null
  }
}
