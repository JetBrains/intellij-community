// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty

class ExpressionConstraint(
  private val expectedType: ExpectedType?,
  private val expression: GrExpression
) : GrConstraintFormula() {

  override fun reduce(session: GroovyInferenceSession, constraints: MutableList<ConstraintFormula>): Boolean {
    if (!session.checkPredicates(expression)) return true
    val leftType = expectedType?.type
    when (expression) {
      is GrMethodCall -> {
        val result = expression.advancedResolve() as? GroovyMethodResult ?: return true
        constraints.add(MethodCallConstraint(expectedType, result, expression))
      }
      is GrNewExpression -> constraints.add(ConstructorCallConstraint(leftType, expression))
      is GrFunctionalExpression -> if (leftType != null) constraints.add(FunctionalExpressionConstraint(expression, leftType))
      is GrSafeCastExpression -> if (leftType != null) constraints.add(SafeCastConstraint(leftType, expression))
      is GrListOrMap -> constraints.add(ListConstraint(leftType, expression))
      is GrReferenceExpression -> {
        val result = expression.rValueReference?.advancedResolve()
        if (result is GroovyMethodResult) {
          constraints += MethodCallConstraint(expectedType, result, expression)
        }
        else if (expectedType != null) {
          constraints += TypePositionConstraint(expectedType, expression.type, expression)
        }
      }
      is GrIndexProperty -> {
        val result = expression.rValueReference?.advancedResolve()
        if (result is GroovyMethodResult) {
          constraints += MethodCallConstraint(expectedType, result, expression)
        }
        else if (expectedType != null) {
          constraints += TypePositionConstraint(expectedType, expression.type, expression)
        }
      }
      is GrAssignmentExpression -> {
        val lValueReference = when (val lValue = expression.lValue) {
          is GrReferenceExpression -> lValue.lValueReference
          is GrIndexProperty -> lValue.lValueReference
          else -> return true
        }
        val result = lValueReference?.advancedResolve() as? GroovyMethodResult ?: return true
        constraints.add(MethodCallConstraint(null, result, expression))
      }
      else -> if (expectedType != null) constraints.add(TypePositionConstraint(expectedType, expression.type, expression))
    }
    return true
  }

  override fun toString(): String = "${expression.text} -> ${expectedType?.type?.presentableText} in ${expectedType?.position}"
}
