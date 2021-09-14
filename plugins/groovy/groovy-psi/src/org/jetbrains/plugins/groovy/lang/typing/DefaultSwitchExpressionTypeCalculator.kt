// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSwitchExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil

class DefaultSwitchExpressionTypeCalculator : GrTypeCalculator<GrSwitchExpression> {
  override fun getType(expression: GrSwitchExpression): PsiType? {
    return TypesUtil.getLeastUpperBoundNullable(expression.caseSections.map(::getCaseSectionType), expression.manager)
  }

  companion object {
    private fun getCaseSectionType(section : GrCaseSection) : PsiType {
      if (section.arrow != null) {
        val statements = section.statements
        val rootStatement = statements.getOrNull(0) ?: return PsiType.NULL
        if (rootStatement is GrExpression) {
          return rootStatement.type ?: PsiType.NULL
        } else if (rootStatement is GrCodeBlock){
          return inferYieldedType(rootStatement.statements)
        }
      } else {
        return inferYieldedType(section.statements)
      }
      return PsiType.NULL
    }
  }
}