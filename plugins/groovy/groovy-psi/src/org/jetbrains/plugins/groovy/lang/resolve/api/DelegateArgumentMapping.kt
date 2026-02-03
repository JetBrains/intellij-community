// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.api

import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType

abstract class DelegateArgumentMapping<out X : CallParameter>(
  val delegate: ArgumentMapping<X>
) : ArgumentMapping<X> {

  override val arguments: Arguments get() = delegate.arguments
  override val varargParameter: X? get() = delegate.varargParameter
  override fun targetParameter(argument: Argument): X? = delegate.targetParameter(argument)
  override fun expectedType(argument: Argument): PsiType? = delegate.expectedType(argument)
  override val expectedTypes: Iterable<Pair<PsiType, Argument>> get() = delegate.expectedTypes
  override fun applicability(): Applicability = delegate.applicability()

  override fun highlightingApplicabilities(substitutor: PsiSubstitutor): ApplicabilityResult = delegate.highlightingApplicabilities(
    substitutor
  )
}
