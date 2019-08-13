// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiParameterList
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.intentions.style.inference.isTypeParameter
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCandidate
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.rawType

class DelegatesToCombiner {
  private var delegateType: PsiType = PsiType.NULL
  private var delegationStrategy: String? = null
  private var delegateParameter: GrParameter? = null

  companion object {
    const val DELEGATES_TO = "DelegatesTo"
    const val TARGET = "Target"
  }

  fun acceptResolveResult(methodResult: GroovyMethodResult) {
    val candidate = methodResult.candidate
    when (candidate?.method?.name) {
      "setDelegate" -> setDelegate(candidate)
      "setResolveStrategy" -> setStrategy(candidate)
      "rehydrate" -> processRehydrate(candidate)
    }
  }

  private fun setDelegateByArgument(arg: Argument) {
    arg.type?.run { delegateType = this; delegateParameter = null }
    (arg as? ExpressionArgument)?.run {
      val parameter = expression.reference?.resolve() as? GrParameter
      if (parameter != null) {
        delegateParameter = parameter
      }
    }
  }

  private fun setDelegate(candidate: GroovyMethodCandidate) {
    setDelegateByArgument(candidate.argumentMapping?.arguments?.singleOrNull() ?: return)
  }

  fun setDelegate(clazz: PsiClass) {
    delegateType = clazz.rawType()
  }

  fun setTypeDelegate(type: PsiType) {
    delegateType = type
  }

  fun setDelegate(expression: GrExpression) {
    delegateType = expression.type ?: PsiType.NULL
    (expression.reference?.resolve() as? GrParameter)?.run { delegateParameter = this }
  }

  fun setStrategy(representation: String) {
    delegationStrategy = representation
  }

  private fun setStrategy(candidate: GroovyMethodCandidate) {
    delegationStrategy = (candidate.argumentMapping?.arguments?.singleOrNull() as? ExpressionArgument)?.expression?.text ?: return
  }

  private fun processRehydrate(candidate: GroovyMethodCandidate) {
    setDelegateByArgument(candidate.argumentMapping?.arguments?.firstOrNull() ?: return)
  }


  fun instantiateAnnotation(outerParameters: PsiParameterList): Pair<String?, List<AnnotatingResult>> {
    val parameter = delegateParameter
    if (delegationStrategy == null && delegateType == PsiType.NULL) {
      return null to emptyList()
    }
    val additionalParameter = instantiateAnnotationForParameter(outerParameters)
    val strategy = delegationStrategy?.run { "strategy = ${this}" } ?: ""
    val delegateTypeRepresentation = delegateType.takeIf { it != PsiType.NULL }?.canonicalText ?: ""
    val type = when {
      parameter != null -> "target = '${parameter.name}'"
      delegateTypeRepresentation.isEmpty() -> ""
      else -> when {
        delegateType.isTypeParameter() -> "type = '$delegateTypeRepresentation'"
        strategy.isNotEmpty() -> "value = $delegateTypeRepresentation"
        else -> delegateTypeRepresentation
      }
    }
    return "@$DELEGATES_TO(${listOf(type, strategy).filter { it.isNotEmpty() }.joinToString()})" to additionalParameter
  }

  private fun instantiateAnnotationForParameter(outerParameters: PsiParameterList): List<AnnotatingResult> {
    val parameter = delegateParameter
    if (parameter == null) {
      return emptyList()
    }
    val realParameter = outerParameters.parameters.find { it.name == parameter.name } ?: return emptyList()
    return listOf(AnnotatingResult(realParameter as? GrParameter ?: return emptyList(),
                                   "@$DELEGATES_TO.$TARGET('${parameter.name}')"))
  }
}