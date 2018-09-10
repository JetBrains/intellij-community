// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.getLeastUpperBoundNullable

class DefaultConditionalTypeCalculator : GrTypeCalculator<GrConditionalExpression> {

  override fun getType(expression: GrConditionalExpression): PsiType? {
    val thenType = expression.thenBranch?.type
    val elseType = expression.elseBranch?.type
    return getLeastUpperBoundNullable(thenType, elseType, expression.manager)
  }
}
