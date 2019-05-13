// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.GrSingleResultResolverProcessor

abstract class FindFirstProcessor<out T : GroovyResolveResult> : ProcessorWithCommonHints(), GrSingleResultResolverProcessor<T> {

  private var myResult: T? = null

  override val result: T? get() = myResult

  final override fun execute(element: PsiElement, state: ResolveState): Boolean {
    if (myResult != null || shouldStop()) return false
    myResult = result(element, state)
    return !shouldStop() && myResult == null
  }

  protected abstract fun result(element: PsiElement, state: ResolveState): T?

  protected open fun shouldStop(): Boolean = false
}
