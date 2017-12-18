/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.ProcessorWithHints
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.GrResolverProcessor

abstract class FindFirstProcessor(name: String) : ProcessorWithHints(), GrResolverProcessor<GroovyResolveResult> {

  init {
    hint(NameHint.KEY, NameHint { name })
  }

  private var result: GroovyResolveResult? = null

  final override val results: List<GroovyResolveResult> get() = result?.let { listOf(it) } ?: emptyList()

  final override fun execute(element: PsiElement, state: ResolveState): Boolean {
    assert(result == null)
    result = result(element, state)
    return result == null
  }

  abstract fun result(element: PsiElement, state: ResolveState): GroovyResolveResult?
}
