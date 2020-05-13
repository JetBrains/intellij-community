// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.api

import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType

interface ArgumentMapping<out P : CallParameter> {

  val arguments: Arguments

  val varargParameter: P? get() = null

  fun targetParameter(argument: Argument): P?

  fun expectedType(argument: Argument): PsiType?

  val expectedTypes: Iterable<Pair<PsiType, Argument>>

  fun applicability(): Applicability

  fun highlightingApplicabilities(substitutor: PsiSubstitutor): ApplicabilityResult
}
