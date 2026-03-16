// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.DelegateArgumentMapping
import org.jetbrains.plugins.groovy.lang.resolve.api.PsiCallParameter

class GdkArgumentMapping(
  method: PsiMethod,
  private val receiverArgument: Argument,
  private val context: PsiElement,
  delegate: ArgumentMapping<PsiCallParameter>
) : DelegateArgumentMapping<PsiCallParameter>(delegate) {

  private val receiverParameter: PsiParameter = method.parameterList.parameters.first()

  override val arguments: Arguments = listOf(receiverArgument) + super.arguments

  override fun targetParameter(argument: Argument): PsiCallParameter? {
    return if (argument == receiverArgument) {
      PsiCallParameterImpl(receiverParameter, PsiSubstitutor.EMPTY, context)
    }
    else {
      super.targetParameter(argument)
    }
  }

  override val expectedTypes: Iterable<Pair<PsiType, Argument>>
    get() = (sequenceOf(Pair(receiverParameter.type, receiverArgument)) + super.expectedTypes).asIterable()
}
