// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.api

import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType

interface ArgumentMapping {

  val arguments: Arguments

  fun targetParameter(argument: Argument): PsiParameter?

  fun expectedType(argument: Argument): PsiType?

  fun isVararg(parameter: PsiParameter): Boolean = false

  val expectedTypes: Iterable<Pair<PsiType, Argument>>

  fun applicability(substitutor: PsiSubstitutor, erase: Boolean): Applicability

  fun highlightingApplicabilities(substitutor: PsiSubstitutor): Applicabilities
}
