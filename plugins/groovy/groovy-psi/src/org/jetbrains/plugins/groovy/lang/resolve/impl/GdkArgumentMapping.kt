// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.resolve.api.*

class GdkArgumentMapping(
  method: PsiMethod,
  private val receiverArgument: Argument,
  val original: ArgumentMapping
) : ArgumentMapping {

  private val receiverParameter: PsiParameter = method.parameterList.parameters.first()

  override val arguments: Arguments = listOf(receiverArgument) + original.arguments

  override fun targetParameter(argument: Argument): PsiParameter? = original.targetParameter(argument)

  override fun expectedType(argument: Argument): PsiType? = original.expectedType(argument)

  override fun isVararg(parameter: PsiParameter): Boolean = original.isVararg(parameter)

  override val expectedTypes: Iterable<Pair<PsiType, Argument>>
    get() {
      return (sequenceOf(Pair(receiverParameter.type, receiverArgument)) + original.expectedTypes).asIterable()
    }

  override fun applicability(substitutor: PsiSubstitutor, erase: Boolean): Applicability = original.applicability(substitutor, erase)

  override fun highlightingApplicabilities(substitutor: PsiSubstitutor): Applicabilities {
    return original.highlightingApplicabilities(substitutor)
  }
}
