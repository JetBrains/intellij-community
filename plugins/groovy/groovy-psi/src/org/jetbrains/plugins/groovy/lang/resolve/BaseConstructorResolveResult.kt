// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.ResolveState
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyConstructorResult

class BaseConstructorResolveResult(
  method: PsiMethod,
  place: PsiElement,
  state: ResolveState,
  arguments: Arguments?,
  override val isMapConstructor: Boolean
) : BaseMethodResolveResult(method, place, state, arguments),
    GroovyConstructorResult
