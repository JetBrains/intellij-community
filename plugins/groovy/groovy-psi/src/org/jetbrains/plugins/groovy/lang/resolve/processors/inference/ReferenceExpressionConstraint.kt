// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.impl.source.resolve.graphInference.constraints.TypeCompatibilityConstraint
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil

class ReferenceExpressionConstraint(private val callRef: GrReferenceExpression, val leftType: PsiType) : ConstraintFormula {

  override fun reduce(session: InferenceSession, constraints: MutableList<ConstraintFormula>): Boolean {
    val resolved = callRef.advancedResolve()
    resolved as? GroovyMethodResult ?: return true
    resolved.candidate?.let {
      val typeParameters = it.method.typeParameters
      val nestedSession = GroovyInferenceSession(typeParameters, it.siteSubstitutor, callRef,
                                                 (session as GroovyInferenceSession).resolveMode)

      nestedSession.propagateVariables(session.inferenceVariables, session.restoreNameSubstitution)

      nestedSession.addConstraint(MethodCallConstraint(callRef, it))
      nestedSession.repeatInferencePhases()
      val returnType = PsiUtil.getSmartReturnType(it.method)
      if (returnType != null && PsiType.VOID != returnType) {
        nestedSession.addConstraint(TypeConstraint(session.substituteWithInferenceVariables(leftType), returnType, callRef))
        nestedSession.repeatInferencePhases()
      }

      session.propagateVariables(nestedSession.inferenceVariables, nestedSession.restoreNameSubstitution)
      for (pair in nestedSession.myIncorporationPhase.captures) {
        session.myIncorporationPhase.addCapture(pair.first, pair.second)
      }
      return true
    }

    callRef.type?.let {
      constraints.add(TypeCompatibilityConstraint(leftType, it))
    }
    return true
  }

  override fun apply(substitutor: PsiSubstitutor, cache: Boolean) {}
}
