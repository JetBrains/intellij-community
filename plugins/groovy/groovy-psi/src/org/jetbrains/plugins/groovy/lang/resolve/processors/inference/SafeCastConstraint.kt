// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.impl.source.resolve.graphInference.constraints.TypeCompatibilityConstraint
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClassTypeElement
import org.jetbrains.plugins.groovy.lang.resolve.DiamondResolveResult

class SafeCastConstraint(private val leftType: PsiType, private val expression: GrSafeCastExpression) : GrConstraintFormula() {

  override fun reduce(session: GroovyInferenceSession, constraints: MutableList<ConstraintFormula>): Boolean {
    val result = (expression.castTypeElement as? GrClassTypeElement)?.referenceElement?.advancedResolve()
    if (result is DiamondResolveResult) {
      val clazz = result.element
      val contextSubstitutor = result.contextSubstitutor
      session.startNestedSession(clazz.typeParameters, contextSubstitutor, expression, result) { nested ->
        val left = nested.substituteWithInferenceVariables(leftType)
        if (left != null) {
          val classType = nested.substituteWithInferenceVariables(contextSubstitutor.substitute(clazz.type()))
          if (classType != null) {
            nested.addConstraint(TypeCompatibilityConstraint(left, classType))
          }
        }
        nested.repeatInferencePhases()
      }
    }
    else {
      constraints.add(TypeConstraint(leftType, expression.type, expression))
    }
    return true
  }

  override fun toString(): String = "${leftType.presentableText} <- ${expression.text}"
}
