// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.impl.source.resolve.graphInference.InferenceBound
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.ExpressionConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.TypeConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

class CollectingGroovyInferenceSession(
  typeParams: Array<PsiTypeParameter>,
  contextSubstitutor: PsiSubstitutor,
  context: PsiElement,
  private val proxyMethodMapping: Map<String, GrParameter> = emptyMap(),
  private val parent: CollectingGroovyInferenceSession? = null,
  private val mirrorBounds: Boolean = false
) : GroovyInferenceSession(typeParams, contextSubstitutor, context, true, emptySet()) {

  override fun substituteWithInferenceVariables(type: PsiType?): PsiType? {
    val result = super.substituteWithInferenceVariables(type)
    if ((result == type || result == null) && parent != null) {
      return parent.substituteWithInferenceVariables(result)
    }
    else {
      return result
    }
  }

  override fun startNestedSession(params: Array<PsiTypeParameter>,
                                  siteSubstitutor: PsiSubstitutor,
                                  context: PsiElement,
                                  result: GroovyResolveResult,
                                  f: (GroovyInferenceSession) -> Unit) {
    val nestedSession = CollectingGroovyInferenceSession(params, siteSubstitutor, context, proxyMethodMapping, this)
    nestedSession.propagateVariables(this)
    f(nestedSession)
    nestedSessions[result] = nestedSession
    this.propagateVariables(nestedSession)
    nestedSession.inferenceVariables.forEach {
      mergeVariables(it, InferenceBound.LOWER)
      mergeVariables(it, InferenceBound.UPPER)
      mergeVariables(it, InferenceBound.EQ)
    }
    for ((vars, rightType) in nestedSession.myIncorporationPhase.captures) {
      this.myIncorporationPhase.addCapture(vars, rightType)
    }
  }

  override fun initArgumentConstraints(mapping: ArgumentMapping?, inferenceSubstitutor: PsiSubstitutor) {
    if (mapping == null) return
    val substitutor = inferenceSubstitutor.putAll(inferenceSubstitution)
    for ((expectedType, argument) in mapping.expectedTypes) {
      val parameter = mapping.targetParameter(argument)
      val resultingParameter = substituteWithInferenceVariables(proxyMethodMapping[parameter?.name]?.type ?: expectedType)
      if (argument is ExpressionArgument) {
        addConstraint(ExpressionConstraint(substitutor.substitute(contextSubstitutor.substitute(resultingParameter)), argument.expression))
      }
      else {
        val type = argument.type
        if (type != null) {
          addConstraint(TypeConstraint(substitutor.substitute(resultingParameter), type, context))
        }
      }
    }
  }

  private fun mergeVariables(variable: InferenceVariable, bound: InferenceBound) {
    variable.getBounds(bound).forEach {
      InferenceVariable.addBound(substituteWithInferenceVariables(variable.parameter.type()), it, bound, this)
      if (mirrorBounds) {
        // todo: try to remove this condition, it is very strange
        InferenceVariable.addBound(substituteWithInferenceVariables(it), variable.type(), negate(bound), this)
      }
    }
  }

  private fun negate(bound: InferenceBound): InferenceBound =
    when (bound) {
      InferenceBound.EQ -> InferenceBound.EQ
      InferenceBound.LOWER -> InferenceBound.UPPER
      InferenceBound.UPPER -> InferenceBound.LOWER
    }
}
