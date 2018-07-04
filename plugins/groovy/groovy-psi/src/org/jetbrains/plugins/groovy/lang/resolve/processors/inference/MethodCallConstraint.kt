// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

class MethodCallConstraint(private val callRef: GrReferenceExpression, val candidate: MethodCandidate) : ConstraintFormula {
  val method = candidate.method
  override fun reduce(session: InferenceSession, constraints: MutableList<ConstraintFormula>): Boolean {
    processArguments(constraints)
    return true
  }

  private fun processArguments(constraints: MutableList<ConstraintFormula>) {
    val argInfos = candidate.mapArguments()

    argInfos.forEach { argument, pair ->
      val leftType = pair.second ?: return@forEach
      if (argument.type != null) {
        constraints.add(TypeConstraint(leftType, argument.type, callRef))
      }

      if (argument.expression != null) {
        constraints.add(ExpressionConstraint(argument.expression, leftType))
      }
    }
  }

  override fun apply(substitutor: PsiSubstitutor, cache: Boolean) {}
}