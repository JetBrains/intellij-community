// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil

class MethodCallConstraint(
  private val leftType: PsiType?,
  private val result: GroovyMethodResult,
  private val context: PsiElement
) : GrConstraintFormula() {

  override fun reduce(session: GroovyInferenceSession, constraints: MutableList<ConstraintFormula>): Boolean {
    val candidate = result.candidate ?: return true
    val method = candidate.method
    val siteSubstitutor = candidate.siteSubstitutor
    session.startNestedSession(method.typeParameters, siteSubstitutor, context, result) { nested ->
      nested.initArgumentConstraints(result.argumentMapping, session.inferenceSubstitution)
      nested.repeatInferencePhases()

      if (leftType != null) {
        val left = session.substituteWithInferenceVariables(session.siteSubstitutor.substitute(leftType))
        if (left != null) {
          val rt = PsiUtil.getSmartReturnType(candidate.method)
          val right = session.substituteWithInferenceVariables(siteSubstitutor.substitute(rt))
          if (right != null && right != PsiType.VOID) {
            nested.addConstraint(TypeConstraint(left, right, context))
            nested.repeatInferencePhases()
          }
        }
      }
    }
    return true
  }
}
