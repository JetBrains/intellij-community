// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.ProcessorWithHints
import com.intellij.util.enumMapOf
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.util.elementInfo
import org.jetbrains.plugins.groovy.lang.resolve.BaseGroovyResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.getName
import org.jetbrains.plugins.groovy.lang.resolve.getResolveKind
import org.jetbrains.plugins.groovy.lang.resolve.sorryCannotKnowElementKind

open class KindsResolverProcessor(
  protected val name: String,
  protected val place: PsiElement,
  protected val kinds: Set<GroovyResolveKind>
) : ProcessorWithHints(),
    NameHint,
    GroovyResolveKind.Hint {

  init {
    @Suppress("LeakingThis") hint(NameHint.KEY, this)
    @Suppress("LeakingThis") hint(GroovyResolveKind.HINT_KEY, this)
  }

  final override fun getName(state: ResolveState): String? = name

  final override fun shouldProcess(kind: GroovyResolveKind): Boolean = kind in kinds

  private val candidates = enumMapOf<GroovyResolveKind, GroovyResolveResult>()

  final override fun execute(element: PsiElement, state: ResolveState): Boolean {
    if (element !is PsiNamedElement) return true
    require(element.isValid) {
      "Invalid element. ${elementInfo(element)}"
    }

    val elementName = getName(state, element)
    if (name != elementName) return true

    val kind = requireNotNull(getResolveKind(element)) {
      "Unknown kind. ${elementInfo(element)}"
    }

    if (state[sorryCannotKnowElementKind] == true) {
      if (kind !in kinds) {
        // return without exception
        return true
      }
    }
    else {
      require(kind in kinds) {
        "Unneeded kind: $kind. ${elementInfo(element)}"
      }
    }

    if (kind in candidates) {
      return true
    }

    candidates[kind] = BaseGroovyResolveResult(element, place, state)
    return true
  }

  fun getCandidate(kind: GroovyResolveKind): GroovyResolveResult? = candidates[kind]

  fun getAllCandidates(): List<GroovyResolveResult> = candidates.values.toList()
}
