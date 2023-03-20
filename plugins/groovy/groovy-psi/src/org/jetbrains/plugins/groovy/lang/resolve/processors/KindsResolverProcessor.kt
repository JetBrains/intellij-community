// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.ProcessorWithHints
import com.intellij.util.containers.enumMapOf
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.util.elementInfo
import org.jetbrains.plugins.groovy.lang.resolve.*

open class KindsResolverProcessor(
  protected val name: String,
  protected val place: PsiElement,
  protected val kinds: Set<GroovyResolveKind>
) : ProcessorWithHints(),
    NameHint,
    GroovyResolveKind.Hint {

  companion object {
    private val propertyKinds = setOf(GroovyResolveKind.VARIABLE, GroovyResolveKind.BINDING, GroovyResolveKind.FIELD, GroovyResolveKind.PROPERTY)
  }

  init {
    @Suppress("LeakingThis") hint(NameHint.KEY, this)
    @Suppress("LeakingThis") hint(GroovyResolveKind.HINT_KEY, this)
  }

  final override fun getName(state: ResolveState): String = name

  override fun shouldProcess(kind: GroovyResolveKind): Boolean = kind in kinds && kind !in candidates

  private val candidates = enumMapOf<GroovyResolveKind, GroovyResolveResult>()

  private fun executeInner(element: PsiElement, state: ResolveState) {
    if (element !is PsiNamedElement && !(element is GrReferenceElement<*> && isReferenceResolveTarget(element))) return
    require(element.isValid) {
      "Invalid element. ${elementInfo(element)}"
    }

    val elementName = getName(state, element)
    if (name != elementName) return

    val kind = getResolveKind(element)
    if (kind == null) {
      log.warn("Unknown kind. ${elementInfo(element)}")
    }
    else if (kind !in kinds) {
      if (state[sorryCannotKnowElementKind] != true) {
        log.error("Unneeded kind: $kind. ${elementInfo(element)}")
      }
    }
    else if (kind !in candidates) {
      val invokedOnProperty = kind in propertyKinds
      candidates[kind] = object : BaseGroovyResolveResult<PsiElement>(element, place, state) {
        override fun isInvokedOnProperty(): Boolean = invokedOnProperty
      }
    }
  }

  final override fun execute(element: PsiElement, state: ResolveState): Boolean {
    executeInner(element, state)
    return kinds.any { it !in candidates }
  }

  fun getCandidate(kind: GroovyResolveKind): GroovyResolveResult? = candidates[kind]

  fun getAllCandidates(): List<GroovyResolveResult> = candidates.values.toList()
}
