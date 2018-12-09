// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument

class GroovyInferenceSession(
  typeParams: Array<PsiTypeParameter>,
  val contextSubstitutor: PsiSubstitutor,
  context: PsiElement,
  val closureSkipList: List<GrMethodCall> = emptyList(),
  val skipClosureBlock: Boolean = true
) : InferenceSession(typeParams, contextSubstitutor, context.manager, context) {

  private val nestedSessions = mutableMapOf<GroovyResolveResult, GroovyInferenceSession>()

  private fun result(): PsiSubstitutor {
    resolveBounds(myInferenceVariables, contextSubstitutor)
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

  fun initArgumentConstraints(mapping: ArgumentMapping?, inferenceSubstitutor: PsiSubstitutor = PsiSubstitutor.EMPTY) {
    if (mapping == null) return
    val substitutor = inferenceSubstitution.putAll(inferenceSubstitutor)
    for ((expectedType, argument) in mapping.expectedTypes) {
      if (argument is ExpressionArgument) {
        addConstraint(ExpressionConstraint(substitutor.substitute(expectedType), argument.expression))
      }
      else {
        val type = argument.type
        if (type != null) {
          addConstraint(TypeConstraint(substitutor.substitute(expectedType), type, context))
        }
      }
    }
  }

  fun startNestedSession(params: Array<PsiTypeParameter>,
                         siteSubstitutor: PsiSubstitutor,
                         context: PsiElement,
                         result: GroovyResolveResult,
                         f: (GroovyInferenceSession) -> Unit) {
    val nestedSession = GroovyInferenceSession(params, siteSubstitutor, context, emptyList(), skipClosureBlock)
    nestedSession.propagateVariables(this)
    f(nestedSession)
    nestedSessions[result] = nestedSession
    this.propagateVariables(nestedSession)
    for ((vars, rightType) in nestedSession.myIncorporationPhase.captures) {
      this.myIncorporationPhase.addCapture(vars, rightType)
    }
  }
}
