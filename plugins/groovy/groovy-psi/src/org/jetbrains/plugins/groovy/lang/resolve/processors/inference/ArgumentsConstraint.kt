// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument

class ArgumentsConstraint(private val candidate: MethodCandidate, private val context: PsiElement) : GrConstraintFormula() {

  override fun reduce(session: GroovyInferenceSession, constraints: MutableList<ConstraintFormula>): Boolean {
    val mapping = candidate.argumentMapping
    for ((argument, parameter) in mapping) {
      val leftType = parameter.second ?: continue
      if (argument is ExpressionArgument) {
        constraints.add(ExpressionConstraint(argument.expression, leftType))
      }
      else {
        val type = argument.type
        if (type != null) {
          constraints.add(TypeConstraint(leftType, type, context))
        }
      }
    }
    return true
  }
}
