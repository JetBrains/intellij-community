// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.api

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType

interface CallSignature<out X : CallParameter> {

  val isVararg: Boolean

  val parameters: List<X>

  val parameterCount: Int
    get() = parameters.size

  val returnType: PsiType?

  fun applyTo(arguments: Arguments, context: PsiElement): ArgumentMapping<@JvmWildcard X>?
}
