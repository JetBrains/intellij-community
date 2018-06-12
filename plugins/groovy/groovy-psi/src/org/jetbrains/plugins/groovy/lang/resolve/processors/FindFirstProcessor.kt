// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.GrResolverProcessor

abstract class FindFirstProcessor<T : GroovyResolveResult> : ProcessorWithCommonHints(), GrResolverProcessor<T> {

  var result: T? = null
    private set

  final override val results: List<T> get() = result?.let { listOf(it) } ?: emptyList()

  final override fun execute(element: PsiElement, state: ResolveState): Boolean {
    if (result != null || shouldStop()) return false
    result = result(element, state)
    return !shouldStop() && result == null
  }

  protected abstract fun result(element: PsiElement, state: ResolveState): T?

  protected open fun shouldStop(): Boolean = false
}
