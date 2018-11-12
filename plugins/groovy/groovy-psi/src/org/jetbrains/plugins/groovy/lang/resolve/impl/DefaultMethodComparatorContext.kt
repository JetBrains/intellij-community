// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.plugins.groovy.lang.resolve.GrMethodComparator
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments

class DefaultMethodComparatorContext(private val place: PsiElement, arguments: Arguments?) : GrMethodComparator.Context {

  private val erasedArguments by lazy {
    arguments?.map(DefaultMethodComparatorContext::ErasedArgument)
  }

  override fun getArguments(): Arguments? = erasedArguments

  override fun getArgumentTypes(): Array<PsiType>? = error("This method must not be called")

  override fun getPlace(): PsiElement = place

  override fun isConstructor(): Boolean = false

  private class ErasedArgument(original: Argument) : Argument {
    override val type: PsiType? by lazy {
      TypeConversionUtil.erasure(original.topLevelType)
    }
  }
}
