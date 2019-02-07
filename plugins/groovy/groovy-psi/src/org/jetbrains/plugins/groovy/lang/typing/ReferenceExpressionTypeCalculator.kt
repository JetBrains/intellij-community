// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("DEPRECATION", "ScheduledForRemoval")

package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrExpressionTypeCalculator

class ReferenceExpressionTypeCalculator : GrTypeCalculator<GrReferenceExpression> {

  override fun getType(expression: GrReferenceExpression): PsiType? {
    val resolved = expression.rValueReference?.resolve()
    for (calculator in GrExpressionTypeCalculator.EP_NAME.extensions) {
      val type = calculator.calculateType(expression, resolved)
      if (type != null) return type
    }
    return null
  }
}
