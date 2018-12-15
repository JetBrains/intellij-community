// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*

class ExpressionConstraint(private val leftType: PsiType?, private val expression: GrExpression) : GrConstraintFormula() {

  override fun reduce(session: GroovyInferenceSession, constraints: MutableList<ConstraintFormula>): Boolean {
    when (expression) {
      is GrMethodCall -> {
        val result = expression.advancedResolve() as? GroovyMethodResult ?: return true
        constraints.add(MethodCallConstraint(leftType, result, expression))
      }
      is GrNewExpression -> constraints.add(ConstructorCallConstraint(leftType, expression))
      is GrClosableBlock -> if (leftType != null) constraints.add(ClosureConstraint(expression, leftType))
      is GrSafeCastExpression -> if (leftType != null) constraints.add(SafeCastConstraint(leftType, expression))
      is GrListOrMap -> constraints.add(ListConstraint(leftType, expression))
      is GrReferenceExpression -> {
        val result = expression.rValueReference?.advancedResolve()
        if (result is GroovyMethodResult) {
          constraints += MethodCallConstraint(leftType, result, expression)
        }
        else if (leftType != null) {
          constraints += TypeConstraint(leftType, expression.type, expression)
        }
      }
      is GrAssignmentExpression -> {
        val result = (expression.lValue as? GrReferenceExpression)?.lValueReference?.advancedResolve() as? GroovyMethodResult ?: return true
        constraints.add(MethodCallConstraint(null, result, expression))
      }
      else -> if (leftType != null) constraints.add(TypeConstraint(leftType, expression.type, expression))
    }
    return true
  }

  override fun toString(): String = "${expression.text} -> ${leftType?.presentableText}"
}
