// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression

class DefaultUnaryExpressionTypeCalculator : GrTypeCalculator<GrUnaryExpression> {

  override fun getType(expression: GrUnaryExpression): PsiType? {
    return if (expression.isPostfix) {
      expression.operand!!.type
    }
    else {
      expression.operationType
    }
  }
}
