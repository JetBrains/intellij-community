// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.lang.java.beans.PropertyKind
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.ProcessorWithHints
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.util.isPropertyName
import org.jetbrains.plugins.groovy.lang.resolve.BaseGroovyResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.GrResolverProcessor
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.filterSameSignatureCandidates
import org.jetbrains.plugins.groovy.lang.resolve.getName
import org.jetbrains.plugins.groovy.lang.resolve.getResolveKind

class AllVariantsProcessor(
  private val name: String,
  private val place: PsiElement
) : ProcessorWithHints(), MultiProcessor, GrResolverProcessor<GroovyResolveResult> {

  init {
    hint(NameHint.KEY, NameHint { name })
  }

  private val accessorProcessors: List<GrResolverProcessor<GroovyResolveResult>> =
    if (!name.isPropertyName()) {
      emptyList()
    }
    else listOf(
      AccessorProcessor(name, PropertyKind.GETTER, null, place),
      AccessorProcessor(name, PropertyKind.BOOLEAN_GETTER, null, place)
    )

  private val allProcessors = listOf(this) + accessorProcessors

  override fun getProcessors(): Collection<PsiScopeProcessor> = allProcessors

  override fun execute(element: PsiElement, state: ResolveState): Boolean {
    val namedElement = element as? PsiNamedElement ?: return true

    if (name != getName(state, namedElement)) return true
    getResolveKind(namedElement) ?: return true

    val candidate = BaseGroovyResolveResult(namedElement, place, state)
    candidates += candidate
    return true
  }

  private val candidates = mutableListOf<GroovyResolveResult>()

  override val results: List<GroovyResolveResult>
    get() {
      val result = mutableListOf<GroovyResolveResult>()
      result += candidates
      accessorProcessors.flatMapTo(result) { it.results }
      return filterSameSignatureCandidates(result).toList()
    }
}
