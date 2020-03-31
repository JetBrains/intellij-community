// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.*
import org.jetbrains.plugins.groovy.lang.resolve.api.*

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
