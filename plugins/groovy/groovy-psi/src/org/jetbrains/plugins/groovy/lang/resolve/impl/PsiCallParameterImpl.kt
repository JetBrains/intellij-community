// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.resolve.api.PsiCallParameter

class PsiCallParameterImpl(
  override val psi: PsiParameter,
  private val substitutor: PsiSubstitutor
) : PsiCallParameter {

  override val type: PsiType get() = substitutor.substitute(psi.type)
  override val parameterName: String? get() = psi.name
  override val isOptional: Boolean get() = psi is GrParameter && psi.isOptional
}