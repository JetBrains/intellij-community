// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.resolve.api.*
import org.jetbrains.plugins.groovy.lang.resolve.api.ApplicabilityResult.ArgumentApplicability

class NullArgumentMapping<out P : CallParameter>(parameter: P) : ArgumentMapping<P> {

  override val arguments: Arguments = singleNullArgumentList

  override val expectedTypes: List<Pair<PsiType, Argument>> = parameter.type?.let { expectedType ->
    listOf(Pair(expectedType, singleNullArgument))
  } ?: emptyList()

  override fun targetParameter(argument: Argument): P? = null

  override fun expectedType(argument: Argument): PsiType? = null

  // call `def foo(String a) {}` with `foo()` is actually `foo(null)`
  // Also see https://issues.apache.org/jira/browse/GROOVY-8248
  override fun applicability(): Applicability = Applicability.applicable

  private companion object {
    private val singleNullArgument = JustTypeArgument(PsiType.NULL)
    private val singleNullArgumentList = listOf(singleNullArgument)
  }

  override fun highlightingApplicabilities(substitutor: PsiSubstitutor): ApplicabilityResult = object : ApplicabilityResult {

    override val applicability: Applicability get() = Applicability.applicable

    override val argumentApplicabilities: Map<Argument, ArgumentApplicability>
      get() = expectedTypes.associate { (type, argument) ->
        argument to ArgumentApplicability(type, Applicability.applicable)
      }
  }
}
