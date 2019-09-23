// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.psi.*
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter.Position.METHOD_PARAMETER
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.*

class CollectingGroovyInferenceSession(
  typeParams: Array<PsiTypeParameter>,
  context: PsiElement,
  contextSubstitutor: PsiSubstitutor = PsiSubstitutor.EMPTY,
  private val proxyMethodMapping: Map<String, GrParameter> = emptyMap(),
  private val parent: CollectingGroovyInferenceSession? = null,
  private val ignoreClosureArguments: Set<GrParameter> = emptySet()
) : GroovyInferenceSession(typeParams, contextSubstitutor, context, true, emptySet()) {


  private fun substituteForeignTypeParameters(type: PsiType?): PsiType? {
    return type?.accept(object : PsiTypeMapper() {
      override fun visitClassType(classType: PsiClassType?): PsiType? {
        if (classType.isTypeParameter()) {
          return myInferenceVariables.find { it.delegate.name == classType?.canonicalText }?.type() ?: classType
        } else {
          return classType
        }
      }
    })
  }

  override fun substituteWithInferenceVariables(type: PsiType?): PsiType? {
    val result = substituteForeignTypeParameters(super.substituteWithInferenceVariables(type))
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
    val nestedSession = CollectingGroovyInferenceSession(params, context, siteSubstitutor, proxyMethodMapping, this, ignoreClosureArguments)
    nestedSession.propagateVariables(this)
    f(nestedSession)
    nestedSessions[result] = nestedSession
    this.propagateVariables(nestedSession)
    for ((vars, rightType) in nestedSession.myIncorporationPhase.captures) {
      this.myIncorporationPhase.addCapture(vars, rightType)
    }
  }

  override fun initArgumentConstraints(mapping: ArgumentMapping?, inferenceSubstitutor: PsiSubstitutor) {
    if (mapping == null) return
    val substitutor = inferenceSubstitutor.putAll(inferenceSubstitution)
    for ((expectedType, argument) in mapping.expectedTypes) {
      val parameter = mapping.targetParameter(argument)
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
