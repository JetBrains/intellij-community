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
    val typeParameters = candidate.method.typeParameters
    val nestedSession = GroovyInferenceSession(typeParameters, candidate.siteSubstitutor, context, emptyList(), session.skipClosureBlock)
    session.nestedSessions[result] = nestedSession
    nestedSession.propagateVariables(session)
    nestedSession.addConstraint(ArgumentsConstraint(candidate, context))
    nestedSession.repeatInferencePhases()
    val returnType = session.siteSubstitutor.substitute(PsiUtil.getSmartReturnType(candidate.method))
    val substitutedLeft = session.siteSubstitutor.substitute(session.substituteWithInferenceVariables(leftType))
    if (returnType != null && PsiType.VOID != returnType && leftType != null) {
      nestedSession.addConstraint(TypeConstraint(substitutedLeft, returnType, context))
      nestedSession.repeatInferencePhases()
    }
    session.propagateVariables(nestedSession)
    for (pair in nestedSession.myIncorporationPhase.captures) {
      session.myIncorporationPhase.addCapture(pair.first, pair.second)
    }
    return true
  }
}
