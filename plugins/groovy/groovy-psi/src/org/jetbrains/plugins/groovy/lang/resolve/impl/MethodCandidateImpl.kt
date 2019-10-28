// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCandidate
import org.jetbrains.plugins.groovy.util.recursionAwareLazy

class MethodCandidateImpl(
  private val receiver: Argument?,
  override val method: PsiMethod,
  erasureSubstitutor: PsiSubstitutor,
  arguments: Arguments?,
  context: PsiElement
) : GroovyMethodCandidate {

  override val receiverType: PsiType? get() = receiver?.type

  override val argumentMapping: ArgumentMapping? by recursionAwareLazy {
    arguments?.let {
      argumentMapping(method, erasureSubstitutor, it, context)
    }
  }
}
