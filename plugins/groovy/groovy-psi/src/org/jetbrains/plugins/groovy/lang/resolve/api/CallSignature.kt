// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.api

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType

interface CallSignature<out X : CallParameter> {

  val isVararg: Boolean

  val parameters: List<X>

  @JvmDefault
  val parameterCount: Int
    get() = parameters.size

  val returnType: PsiType?

  fun applyTo(arguments: Arguments, context: PsiElement): ArgumentMapping<@JvmWildcard X>?
}
