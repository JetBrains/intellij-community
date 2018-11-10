// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall

class GroovyInferenceSession(
  typeParams: Array<PsiTypeParameter>,
  val siteSubstitutor: PsiSubstitutor,
  context: PsiElement,
  val closureSkipList: List<GrMethodCall> = emptyList(),
  val skipClosureBlock: Boolean = true
) : InferenceSession(typeParams, siteSubstitutor, context.manager, context) {

  val nestedSessions = mutableMapOf<GroovyResolveResult, GroovyInferenceSession>()

  private fun result(): PsiSubstitutor {
    resolveBounds(myInferenceVariables, siteSubstitutor)
    return prepareSubstitution()
  }

  fun inferSubst(): PsiSubstitutor {
    repeatInferencePhases()
    return result()
  }

  fun inferSubst(result: GroovyResolveResult): PsiSubstitutor {
    repeatInferencePhases()
    findSession(result)?.let {
      return it.result()
    }
    return PsiSubstitutor.EMPTY
  }

  private fun findSession(result: GroovyResolveResult): GroovyInferenceSession? {
    nestedSessions[result]?.let {
      return it
    }
    for (nested in nestedSessions.values) {
      nested.findSession(result)?.let {
        return it
      }
    }
    return null
  }
}
