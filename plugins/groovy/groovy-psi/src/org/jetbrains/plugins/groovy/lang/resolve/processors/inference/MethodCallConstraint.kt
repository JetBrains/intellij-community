// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
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
    val siteSubstitutor = if (method.isConstructor) {
      result.contextSubstitutor
    }
    else {
      candidate.siteSubstitutor
    }
    val typeParameters = if (method.isConstructor) {
      method.typeParameters + (method.containingClass?.typeParameters ?: PsiTypeParameter.EMPTY_ARRAY)
    }
    else {
      method.typeParameters
    }
    session.startNestedSession(typeParameters, siteSubstitutor, context, result) { nested ->
      nested.initArgumentConstraints(result.argumentMapping)
      nested.repeatInferencePhases()
      val rt = if (method.isConstructor) {
        method.containingClass?.type()
      }
      else {
        PsiUtil.getSmartReturnType(method)
      }
      val substitutedRT = session.substituteWithInferenceVariables(siteSubstitutor.substitute(rt))
      val substitutedLeft = session.substituteWithInferenceVariables(session.siteSubstitutor.substitute(leftType))
      if (substitutedRT != null && PsiType.VOID != substitutedRT && leftType != null) {
        nested.addConstraint(TypeConstraint(substitutedLeft, substitutedRT, context))
        nested.repeatInferencePhases()
      }
    }
    return true
  }
}
