// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.resolve.api.*

class NullArgumentMapping(parameter: PsiParameter) : ArgumentMapping {

  override val arguments: Arguments = singleNullArgumentList

  override val expectedTypes: List<Pair<PsiType, Argument>> = listOf(Pair(parameter.type, singleNullArgument))

  override fun targetParameter(argument: Argument): PsiParameter? = null

  override fun expectedType(argument: Argument): PsiType? = null

  // call `def foo(String a) {}` with `foo()` is actually `foo(null)`
  // Also see https://issues.apache.org/jira/browse/GROOVY-8248
  override fun applicability(substitutor: PsiSubstitutor, erase: Boolean): Applicability = Applicability.applicable

  private companion object {
    private val singleNullArgument = JustTypeArgument(PsiType.NULL)
    private val singleNullArgumentList = listOf(singleNullArgument)
  }

  override fun highlightingApplicabilities(substitutor: PsiSubstitutor): Applicabilities {
    return expectedTypes.associate { (type, argument) -> argument to ApplicabilityData(type, Applicability.applicable)}
  }
}
