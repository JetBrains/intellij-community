// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.ResolveState
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments

open class AccessorResolveResult(
  element: PsiMethod,
  place: PsiElement,
  state: ResolveState,
  arguments: Arguments?
) : BaseMethodResolveResult(element, place, state, arguments) {

  final override fun isInvokedOnProperty(): Boolean = true
}
