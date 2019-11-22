// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil

class MethodCallConstraint(
  private val expectedType: ExpectedType?,
  private val result: GroovyMethodResult,
  private val context: PsiElement
) : GrConstraintFormula() {

  override fun reduce(session: GroovyInferenceSession, constraints: MutableList<ConstraintFormula>): Boolean {
    val candidate = result.candidate ?: return true
    val method = candidate.method
    val contextSubstitutor = result.contextSubstitutor
    session.startNestedSession(method.typeParameters, contextSubstitutor, context, result) { nested ->
      nested.initArgumentConstraints(candidate.argumentMapping)
      nested.repeatInferencePhases()

      if (expectedType != null) {
        val left = nested.substituteWithInferenceVariables(contextSubstitutor.substitute(expectedType.type))
        if (left != null) {
          val rt = SpreadState.apply(PsiUtil.getSmartReturnType(method), result.spreadState, context.project)
          val right = nested.substituteWithInferenceVariables(contextSubstitutor.substitute(rt))
          if (right != null && right != PsiType.VOID) {
            nested.addConstraint(TypePositionConstraint(expectedType, right, context))
            nested.repeatInferencePhases()
          }
        }
      }
    }
    return true
  }
}
