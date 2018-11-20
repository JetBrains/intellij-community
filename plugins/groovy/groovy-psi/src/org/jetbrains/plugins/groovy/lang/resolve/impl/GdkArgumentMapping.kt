// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping

class GdkArgumentMapping(
  private val method: PsiMethod,
  private val receiverArgument: Argument,
  private val original: ArgumentMapping
) : ArgumentMapping {

  override val applicability: Applicability get() = original.applicability

  override val expectedTypes: Iterable<Pair<PsiType, Argument>>
    get() {
      return (sequenceOf(Pair(method.parameterList.parameters.first().type, receiverArgument)) + original.expectedTypes).asIterable()
    }
}
