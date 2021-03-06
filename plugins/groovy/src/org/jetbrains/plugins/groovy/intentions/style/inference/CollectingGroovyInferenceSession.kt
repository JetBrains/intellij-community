// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.InferenceBound
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter.Position.METHOD_PARAMETER
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.api.PsiCallParameter
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.*

class CollectingGroovyInferenceSession(
  private val typeParams: Array<PsiTypeParameter>,
  context: PsiElement,
  contextSubstitutor: PsiSubstitutor = PsiSubstitutor.EMPTY,
  private val proxyMethodMapping: Map<String, GrParameter> = emptyMap(),
  private val ignoreClosureArguments: Set<GrParameter> = emptySet(),
  private val skipClosureArguments: Boolean = false,
  private val expressionFilters: Set<(GrExpression) -> Boolean> = emptySet(),
  private val depth: Int = 0
) : GroovyInferenceSession(typeParams, contextSubstitutor, context, skipClosureArguments, expressionFilters) {


  companion object {
    private const val MAX_DEPTH = 127
    private const val OBSERVING_DISTANCE = 10

    fun getContextSubstitutor(resolveResult: GroovyMethodResult,
                              nearestCall: GrCall): PsiSubstitutor = RecursionManager.doPreventingRecursion(resolveResult, true) {
      val collectingSession = CollectingGroovyInferenceSession(nearestCall.parentOfType<GrMethod>()!!.typeParameters, nearestCall)
      findExpression(nearestCall)?.let(collectingSession::addExpression)
      collectingSession.inferSubst(resolveResult)
    } ?: PsiSubstitutor.EMPTY
  }

  private fun substituteForeignTypeParameters(type: PsiType?): PsiType? {
    return type?.accept(object : PsiTypeMapper() {
      override fun visitClassType(classType: PsiClassType): PsiType {
        if (classType.isTypeParameter()) {
          return myInferenceVariables.find { it.delegate.name == classType.canonicalText }?.type() ?: classType
        }
        else {
          return classType
        }
      }
    })
  }

  override fun addConstraint(constraint: ConstraintFormula?) {
    if (constraint is MethodCallConstraint) {
      val method = constraint.result.candidate?.method ?: return
      if (method.returnTypeElement == null) {
        return super.addConstraint(MethodCallConstraint(null, constraint.result, constraint.context))
      }
    }
    return super.addConstraint(constraint)
  }

  override fun substituteWithInferenceVariables(type: PsiType?): PsiType? =
    substituteForeignTypeParameters(super.substituteWithInferenceVariables(type))

  override fun inferSubst(result: GroovyResolveResult): PsiSubstitutor {
    repeatInferencePhases()
    val nested = findSession(result) ?: return PsiSubstitutor.EMPTY
    for (param in typeParams) {
      nested.getInferenceVariable(substituteWithInferenceVariables(param.type())).instantiation = param.type()
    }
    return nested.result()
  }

  override fun startNestedSession(params: Array<PsiTypeParameter>,
                                  siteSubstitutor: PsiSubstitutor,
                                  context: PsiElement,
                                  result: GroovyResolveResult,
                                  f: (GroovyInferenceSession) -> Unit) {
    if (depth >= MAX_DEPTH) {
      var place = context
      repeat(OBSERVING_DISTANCE) {
        place = place.parent ?: place
      }
      throw AssertionError("Inference process has gone too deep on ${context.text} in ${place.text}")
    }
    val nestedSession = CollectingGroovyInferenceSession(params, context, siteSubstitutor, proxyMethodMapping, ignoreClosureArguments,
                                                         skipClosureArguments, expressionFilters, depth + 1)
    nestedSession.propagateVariables(this)
    f(nestedSession)
    mirrorInnerVariables(nestedSession)
    nestedSessions[result] = nestedSession
    this.propagateVariables(nestedSession)
    for ((vars, rightType) in nestedSession.myIncorporationPhase.captures) {
      this.myIncorporationPhase.addCapture(vars, rightType)
    }
  }

  private fun mirrorInnerVariables(session: CollectingGroovyInferenceSession) {
    for (variable in session.inferenceVariables) {
      if (variable in this.inferenceVariables) {
        mirrorBound(variable, InferenceBound.LOWER, session)
        mirrorBound(variable, InferenceBound.EQ, session)
        mirrorBound(variable, InferenceBound.UPPER, session)
      }
    }
  }

  private fun mirrorBound(outerVariable: InferenceVariable, bound: InferenceBound, session: CollectingGroovyInferenceSession) {
    for (boundType in outerVariable.getBounds(bound)) {
      val innerVariable = session.getInferenceVariable(boundType)?.takeIf { it !in this.inferenceVariables } ?: continue
      InferenceVariable.addBound(innerVariable.type(), outerVariable.type(), negateInferenceBound(bound), session)
    }
  }

  private fun negateInferenceBound(bound: InferenceBound): InferenceBound =
    when (bound) {
      InferenceBound.LOWER -> InferenceBound.UPPER
      InferenceBound.EQ -> InferenceBound.EQ
      InferenceBound.UPPER -> InferenceBound.LOWER
    }

  override fun initArgumentConstraints(mapping: ArgumentMapping<PsiCallParameter>?, inferenceSubstitutor: PsiSubstitutor) {
    if (mapping == null) return
    val substitutor = inferenceSubstitutor.putAll(inferenceSubstitution)
    for ((expectedType, argument) in mapping.expectedTypes) {
      val parameter = mapping.targetParameter(argument)?.psi
      if (proxyMethodMapping[parameter?.name] in ignoreClosureArguments && argument.type.isClosureTypeDeep()) {
        continue
      }
      val resultingParameter = substituteWithInferenceVariables(proxyMethodMapping[parameter?.name]?.type ?: expectedType)
      if (argument is ExpressionArgument) {
        val leftType = substitutor.substitute(contextSubstitutor.substitute(resultingParameter))
        addConstraint(ExpressionConstraint(ExpectedType(leftType, METHOD_PARAMETER), argument.expression))
      }
      else {
        val type = argument.type
        if (type != null) {
          addConstraint(TypeConstraint(substitutor.substitute(resultingParameter), type, context))
        }
      }
    }
  }
}
