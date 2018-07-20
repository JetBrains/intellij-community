// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

class GroovyInferenceSession(typeParams: Array<PsiTypeParameter>,
                             val siteSubstitutor: PsiSubstitutor,
                             context: PsiElement,
                             val skipClosureBlock: Boolean = true) : InferenceSession(typeParams, siteSubstitutor, context.manager, context) {

  val myNestedSessions = mutableMapOf<GrReferenceExpression, GroovyInferenceSession>()

  fun result(): PsiSubstitutor {
    resolveBounds(myInferenceVariables, siteSubstitutor)
    return prepareSubstitution()
  }

  fun inferSubst(): PsiSubstitutor {
    repeatInferencePhases()
    return result()
  }

  fun inferSubst(ref: GrReferenceExpression): PsiSubstitutor {
    repeatInferencePhases()
    findSession(ref)?.let { return it.result() }
    return PsiSubstitutor.EMPTY
  }

  private fun findSession(ref: GrReferenceExpression): GroovyInferenceSession? {
    myNestedSessions[ref]?.let { return it }
    myNestedSessions.values.forEach { nested -> nested.findSession(ref)?.let { return it }}
    return null
  }
}
