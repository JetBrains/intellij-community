// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
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
      val yields = ControlFlowUtils.collectYields(flow.flow).takeIf(List<*>::isNotEmpty) ?: return PsiTypes.nullType()
      return TypesUtil.getLeastUpperBoundNullable(yields.map { stmt: GrStatement ->
        when (stmt) {
          is GrYieldStatement -> stmt.yieldedValue?.type
          is GrExpression -> stmt.type
          else -> PsiTypes.nullType()
        }
      }, section.manager) ?: PsiTypes.nullType()
    }
  }
}