// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiParameterList
import com.intellij.psi.PsiType
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.intentions.style.inference.isTypeParameter
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_DELEGATES_TO
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_DELEGATES_TO_TARGET
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCandidate
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.rawType

class DelegatesToCombiner {
  private var delegateType: PsiType = PsiType.NULL
  private var delegationStrategy: String? = null
  private var delegateParameter: GrParameter? = null

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

  private fun getName(parameter: PsiParameter): String =
    parameter.name


  fun instantiateAnnotation(outerParameters: PsiParameterList): Pair<String?, AnnotatingResult?> {
    if (delegationStrategy == null && delegateType == PsiType.NULL) {
      return null to null
    }
    val strategy = delegationStrategy?.run { "strategy = ${this}" } ?: ""
    val delegateTypeRepresentation = delegateType.takeIf { it != PsiType.NULL }?.canonicalText ?: ""
    val parameter = delegateParameter
    val typeRepresentation = when {
      parameter != null -> "target = '${getName(parameter)}'"
      delegateType.anyComponent { it.isTypeParameter() } -> "type = '$delegateTypeRepresentation'"
      strategy.isNotEmpty() -> "value = $delegateTypeRepresentation"
      else -> delegateTypeRepresentation
    }
    val parameterResult = instantiateAnnotationForParameter(outerParameters)
    val annotationText = "@$GROOVY_LANG_DELEGATES_TO(${listOf(typeRepresentation, strategy).filter { it.isNotEmpty() }.joinToString()})"
    return annotationText to parameterResult
  }

  private fun instantiateAnnotationForParameter(outerParameters: PsiParameterList): AnnotatingResult? {
    val parameter = delegateParameter ?: return null
    val parameterList = parameter.parentOfType<PsiParameterList>() ?: return null
    val actualParameter = outerParameters.parameters[parameterList.getParameterIndex(parameter)] ?: return null
    return AnnotatingResult(actualParameter as? GrParameter ?: return null, "@$GROOVY_LANG_DELEGATES_TO_TARGET('${getName(parameter)}')")
  }
}