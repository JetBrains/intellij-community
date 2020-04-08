// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCandidate
import org.jetbrains.plugins.groovy.lang.resolve.api.PsiCallParameter

class GdkMethodCandidate(
  override val method: PsiMethod,
  receiverArgument: Argument,
  context: PsiElement,
  originalMapping: ArgumentMapping<PsiCallParameter>
) : GroovyMethodCandidate {

  override val receiverType: PsiType? get() = null

  override val argumentMapping: ArgumentMapping<PsiCallParameter>? = GdkArgumentMapping(method, receiverArgument, context, originalMapping)
}
