// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter.Position.METHOD_PARAMETER
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument

open class GroovyInferenceSession(
  typeParams: Array<PsiTypeParameter>,
  val contextSubstitutor: PsiSubstitutor,
  context: PsiElement,
  val skipClosureBlock: Boolean = true,
  private val expressionPredicates: Set<ExpressionPredicate> = emptySet()
) : InferenceSession(typeParams, contextSubstitutor, context.manager, context) {

  protected val nestedSessions = mutableMapOf<GroovyResolveResult, GroovyInferenceSession>()

  fun result(): PsiSubstitutor {
    resolveBounds(myInferenceVariables, contextSubstitutor)
    return prepareSubstitution()
  }

  fun inferSubst(): PsiSubstitutor {
    repeatInferencePhases()
    return result()
  }

  open fun inferSubst(result: GroovyResolveResult): PsiSubstitutor {
    repeatInferencePhases()
    return findSession(result)?.result() ?: PsiSubstitutor.EMPTY
  }

  protected fun findSession(result: GroovyResolveResult): GroovyInferenceSession? {
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

  open fun initArgumentConstraints(mapping: ArgumentMapping?, inferenceSubstitutor: PsiSubstitutor = PsiSubstitutor.EMPTY) {
    if (mapping == null) return
    val substitutor = inferenceSubstitutor.putAll(inferenceSubstitution)
    for ((expectedType, argument) in mapping.expectedTypes) {
      if (argument is ExpressionArgument) {
        val leftType = substitutor.substitute(contextSubstitutor.substitute(expectedType))
        addConstraint(ExpressionConstraint(ExpectedType(leftType, METHOD_PARAMETER), argument.expression))
      }
      else {
        val type = argument.type
        if (type != null) {
          addConstraint(TypePositionConstraint(ExpectedType(substitutor.substitute(expectedType), METHOD_PARAMETER), type, context))
        }
      }
    }
  }

  fun registerReturnTypeConstraints(expectedType: ExpectedType, right: PsiType, context: PsiElement) {
    val right_ = if (isErased) {
      val currentSubstitutor = resolveSubset(myInferenceVariables, contextSubstitutor)
      TypeConversionUtil.erasure(currentSubstitutor.substitute(right))
    }
    else {
      substituteWithInferenceVariables(contextSubstitutor.substitute(right))
    }
    addConstraint(TypePositionConstraint(expectedType, right_, context))
  }

  open fun startNestedSession(params: Array<PsiTypeParameter>,
                              siteSubstitutor: PsiSubstitutor,
                              context: PsiElement,
                              result: GroovyResolveResult,
                              f: (GroovyInferenceSession) -> Unit) {
    val nestedSession = GroovyInferenceSession(params, siteSubstitutor, context, skipClosureBlock, expressionPredicates)
    nestedSession.propagateVariables(this)
    f(nestedSession)
    nestedSessions[result] = nestedSession
    this.propagateVariables(nestedSession)
    for ((vars, rightType) in nestedSession.myIncorporationPhase.captures) {
      this.myIncorporationPhase.addCapture(vars, rightType)
    }
  }

  fun checkPredicates(expression: GrExpression): Boolean {
    return expressionPredicates.all { it.invoke(expression) }
  }
}
