// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.ProcessorWithHints
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable
import org.jetbrains.plugins.groovy.lang.resolve.ElementGroovyResult
import org.jetbrains.plugins.groovy.lang.resolve.GrResolverProcessor

class LocalVariableProcessor(name: String) : ProcessorWithHints(), GrResolverProcessor<GroovyResolveResult> {

  init {
    hint(NameHint.KEY, NameHint { name })
    hint(ElementClassHint.KEY, ElementClassHint { false })
    hint(GroovyResolveKind.HINT_KEY, GroovyResolveKind.Hint { it === GroovyResolveKind.VARIABLE })
  }

  private var resolveResult: GroovyResolveResult? = null

  override val results: List<GroovyResolveResult> get() = resolveResult?.let { listOf(it) } ?: emptyList()

  override fun execute(element: PsiElement, state: ResolveState): Boolean {
    if (element !is GrVariable || element is GrField) return true
    assert(element !is GrBindingVariable)
    resolveResult = ElementGroovyResult(element)
    return false
  }
}
