// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.impl.source.resolve.graphInference.constraints.TypeCompatibilityConstraint
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression

class ConstructorCallConstraint(private val leftType: PsiType?, private val expression: GrNewExpression) : GrConstraintFormula() {

  override fun reduce(session: GroovyInferenceSession, constraints: MutableList<ConstraintFormula>): Boolean {
    val reference = expression.referenceElement ?: return true
    val result = reference.advancedResolve()
    val clazz = result.element as? PsiClass ?: return true
    val contextSubstitutor = result.contextSubstitutor

    session.startNestedSession(clazz.typeParameters, contextSubstitutor, expression, result) { nested ->
      val constructorResult = expression.advancedResolve() as? GroovyMethodResult
      if (constructorResult != null) {
        val constraint = MethodCallConstraint(null, constructorResult, expression) // leftType is null to only process arguments
        nested.addConstraint(constraint)
        nested.repeatInferencePhases()
      }
      if (leftType != null) {
        val left = nested.substituteWithInferenceVariables(leftType)
        if (left != null) {
          val classType = nested.substituteWithInferenceVariables(contextSubstitutor.substitute(clazz.type()))
          if (classType != null) {
            nested.addConstraint(TypeCompatibilityConstraint(left, classType))
          }
        }
      }
      nested.repeatInferencePhases()
    }
    return true
  }

  override fun toString(): String = "${expression.text} -> ${leftType?.presentableText}"
}
