// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument

class ArgumentsConstraint(private val mapping: ArgumentMapping, private val context: PsiElement) : GrConstraintFormula() {

  override fun reduce(session: GroovyInferenceSession, constraints: MutableList<ConstraintFormula>): Boolean {
    for ((expectedType, argument) in mapping.expectedTypes) {
      if (argument is ExpressionArgument) {
        constraints.add(ExpressionConstraint(expectedType, argument.expression))
      }
      else {
        val type = argument.type
        if (type != null) {
          constraints.add(TypeConstraint(expectedType, type, context))
        }
      }
    }
    return true
  }
}
