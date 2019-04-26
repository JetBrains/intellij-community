// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.ResolveState
import com.intellij.util.recursionSafeLazy
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.buildTopLevelSession

class GenericAccessorResolveResult(
  element: PsiMethod,
  place: PsiElement,
  state: ResolveState,
  arguments: Arguments?
) : AccessorResolveResult(element, place, state, arguments) {

  override fun getSubstitutor(): PsiSubstitutor = inferredSubstitutor ?: run {
    log.warn("Recursion prevented")
    PsiSubstitutor.EMPTY
  }

  private val inferredSubstitutor by recursionSafeLazy {
    buildTopLevelSession(place).inferSubst(this)
  }
}
