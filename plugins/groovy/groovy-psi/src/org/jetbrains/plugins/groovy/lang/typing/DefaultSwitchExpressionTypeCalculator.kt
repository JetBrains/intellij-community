// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrYieldStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSwitchExpression
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil

class DefaultSwitchExpressionTypeCalculator : GrTypeCalculator<GrSwitchExpression> {
  override fun getType(expression: GrSwitchExpression): PsiType? {
    return TypesUtil.getLeastUpperBoundNullable(expression.caseSections.map(::getCaseSectionType), expression.manager)
  }

  companion object {
    private fun getCaseSectionType(section : GrCaseSection) : PsiType {
      val flow = ControlFlowBuilder.buildControlFlow(section)
      val yields = ControlFlowUtils.collectYields(flow).takeIf(List<*>::isNotEmpty) ?: return PsiType.NULL
      return TypesUtil.getLeastUpperBoundNullable(yields.map { stmt: GrStatement ->
        when (stmt) {
          is GrYieldStatement -> stmt.yieldedValue?.type
          is GrExpression -> stmt.type
          else -> PsiType.NULL
        }
      }, section.manager) ?: PsiType.NULL
    }
  }
}