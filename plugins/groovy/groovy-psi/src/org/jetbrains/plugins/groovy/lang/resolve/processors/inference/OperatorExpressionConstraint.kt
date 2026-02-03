// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrOperatorExpression

class OperatorExpressionConstraint(private val expression: GrOperatorExpression) : GrConstraintFormula() {

  override fun reduce(session: GroovyInferenceSession, constraints: MutableList<in ConstraintFormula>): Boolean {
    val reference = expression.reference
    if (reference != null) {
      val result = reference.advancedResolve()
      if (result is GroovyMethodResult) {
        constraints.add(MethodCallConstraint(null, result, expression))
      }
    }
    return true
  }

  override fun toString(): String = "${expression.text} -> null"
}
