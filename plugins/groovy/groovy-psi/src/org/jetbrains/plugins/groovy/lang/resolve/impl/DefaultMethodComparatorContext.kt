// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.resolve.GrMethodComparator
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.ErasedArgument

class DefaultMethodComparatorContext(private val place: PsiElement, arguments: Arguments?) : GrMethodComparator.Context {

  private val erasedArguments by lazy {
    arguments?.map(::ErasedArgument)
  }

  override fun getArguments(): Arguments? = erasedArguments

  override fun getArgumentTypes(): Array<PsiType>? = error("This method must not be called")

  override fun getPlace(): PsiElement = place

  override fun isConstructor(): Boolean = false
}
