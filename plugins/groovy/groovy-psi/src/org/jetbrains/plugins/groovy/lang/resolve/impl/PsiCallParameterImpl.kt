// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.impl.PsiClassImplUtil
import com.intellij.util.recursionSafeLazy
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.resolve.api.PsiCallParameter

class PsiCallParameterImpl(
  override val psi: PsiParameter,
  private val substitutor: PsiSubstitutor,
  private val context: PsiElement
) : PsiCallParameter {

  override val type: PsiType? by recursionSafeLazy {
    PsiClassImplUtil.correctType(substitutor.substitute(psi.type), context.resolveScope)
  }

  override val parameterName: String get() = psi.name

  override val isOptional: Boolean get() = psi is GrParameter && psi.isOptional
}
