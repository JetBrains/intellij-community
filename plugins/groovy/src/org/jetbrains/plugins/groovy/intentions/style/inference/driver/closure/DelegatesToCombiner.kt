// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.intentions.style.inference.isTypeParameter
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCandidate

class DelegatesToCombiner {
  private var delegateType: PsiType = PsiType.NULL
  private var delegationStrategy: GrExpression? = null

  companion object {
    const val DELEGATES_TO = "DelegatesTo"
  }

  fun acceptResolveResult(methodResult: GroovyMethodResult) {
    val candidate = methodResult.candidate
    when (candidate?.method?.name) {
      "setDelegate" -> setDelegate(candidate)
      "setResolveStrategy" -> setStrategy(candidate)
      "rehydrate" -> processRehydrate(candidate)
    }
  }


  private fun setDelegate(candidate: GroovyMethodCandidate) {
    delegateType = candidate.argumentMapping?.arguments?.singleOrNull()?.type ?: return
  }

  private fun setStrategy(candidate: GroovyMethodCandidate) {
    delegationStrategy = (candidate.argumentMapping?.arguments?.singleOrNull() as? ExpressionArgument)?.expression ?: return
  }

  private fun processRehydrate(candidate: GroovyMethodCandidate) {
    delegateType = candidate.argumentMapping?.arguments?.firstOrNull()?.type ?: return
  }


  fun instantiateAnnotation(context: PsiElement): GrAnnotation? {
    if (delegateType != PsiType.NULL) {
      // todo: delegating to parameters and generic types
      val strategy = delegationStrategy?.run { "strategy = ${text}" } ?: ""
      val delegateTypeRepresentation = delegateType.takeIf { it != PsiType.NULL }?.canonicalText ?: ""
      val type = when {
        delegateTypeRepresentation.isEmpty() -> ""
        else -> when {
                  delegateType.isTypeParameter() -> "type = "
                  strategy.isNotEmpty() -> "value = "
                  else -> ""
                } + delegateTypeRepresentation
      }
      return GroovyPsiElementFactory.getInstance(context.project).createAnnotationFromText(
        "@$DELEGATES_TO(${listOf(type, strategy).filter { it.isNotEmpty() }.joinToString()})", context
      )
    }
    return null
  }
}