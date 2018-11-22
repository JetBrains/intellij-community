// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.util.isOptional
import org.jetbrains.plugins.groovy.lang.resolve.api.*

class EmptyArgumentsMapping(method: PsiMethod) : ArgumentMapping {

  override val arguments: Arguments

  override val expectedTypes: List<Pair<PsiType, Argument>>

  init {
    val parameter = method.parameterList.parameters.singleOrNull()
    if (parameter == null || parameter.isOptional) {
      // call `def foo(a = 1) {}` with `foo()`
      arguments = emptyList()
      expectedTypes = emptyList()
    }
    else {
      arguments = listOf(singleNullArgument)
      expectedTypes = listOf(Pair(parameter.type, singleNullArgument))
    }
  }

  override fun targetParameter(argument: Argument): PsiParameter? = null

  override fun expectedType(argument: Argument): PsiType? = null

  override val applicability: Applicability by lazy(fun(): Applicability {
    val parameters = method.parameterList.parameters
    if (parameters.isEmpty()) {
      // call `def foo() {}` with `foo()`
      return Applicability.applicable
    }
    val parameter = parameters.singleOrNull() ?: return Applicability.inapplicable
    if (parameter.isOptional) {
      // call `def foo(a = 1) {}` with `foo()`
      return Applicability.applicable
    }
    if (parameter.type is PsiClassType) {
      // call `def foo(String a) {}` with `foo()` is actually `foo(null)`
      // https://issues.apache.org/jira/browse/GROOVY-8248
      return Applicability.applicable
    }
    return Applicability.inapplicable
  })

  private companion object {
    private val singleNullArgument = JustTypeArgument(PsiType.NULL)
  }
}
