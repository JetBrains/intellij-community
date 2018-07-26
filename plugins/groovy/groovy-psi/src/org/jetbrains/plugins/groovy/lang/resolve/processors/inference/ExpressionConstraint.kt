// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

class ExpressionConstraint(val expression: GrExpression, val leftType: PsiType?) : ConstraintFormula {
  override fun reduce(session: InferenceSession, constraints: MutableList<ConstraintFormula>): Boolean {
    when (expression) {
      is GrMethodCall -> {
        val invokedExpression = expression.invokedExpression
        when (invokedExpression) {
          is GrReferenceExpression -> constraints.add(ReferenceExpressionConstraint(invokedExpression, leftType))
          else -> return true
        }
      }
      is GrClosableBlock -> if (leftType != null) constraints.add(ClosureConstraint(expression, leftType))
      else -> if (leftType != null) constraints.add(TypeConstraint(leftType, expression.type, expression))
    }
    return true
  }

  override fun apply(substitutor: PsiSubstitutor, cache: Boolean) {}
}
