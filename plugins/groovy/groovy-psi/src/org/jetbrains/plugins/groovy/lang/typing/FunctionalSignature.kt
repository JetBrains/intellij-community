// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.CallParameter
import org.jetbrains.plugins.groovy.lang.resolve.api.CallSignature
import org.jetbrains.plugins.groovy.lang.resolve.impl.argumentMapping
import org.jetbrains.plugins.groovy.util.recursionAwareLazy

class FunctionalSignature(
  private val functionalExpression: GrFunctionalExpression,
  private val skippedOptionalParameterCount: Int
) : CallSignature<CallParameter> {

  override val isVararg: Boolean by recursionAwareLazy {
    functionalExpression.isVarArgs
  }

  override val parameters: List<CallParameter> by recursionAwareLazy {
    skipOptionalParams().map(::ClosureParameter)
  }

  private fun skipOptionalParams(): List<GrParameter> {
    val parameters: Array<GrParameter> = functionalExpression.allParameters

    if (skippedOptionalParameterCount == 0) {
      return parameters.asList()
    }

    val resultSize = parameters.size - skippedOptionalParameterCount
    val result = ArrayList<GrParameter>(resultSize)
    var optionalParametersToSkip = skippedOptionalParameterCount
    for (parameter in parameters.asList().asReversed()) {
      if (optionalParametersToSkip > 0 && parameter.isOptional) {
        optionalParametersToSkip--
      }
      else {
        result += parameter
      }
    }
    require(result.size == resultSize)
    return result.asReversed()
  }

  override val returnType: PsiType? get() = functionalExpression.returnType

  override fun applyTo(arguments: Arguments, context: PsiElement): ArgumentMapping<CallParameter>? {
    return argumentMapping(this, arguments, context)
  }

  private class ClosureParameter(private val parameter: GrParameter) : CallParameter {
    override val type: PsiType get() = parameter.type
    override val parameterName: String? get() = parameter.name
    override val isOptional: Boolean get() = false
  }
}
