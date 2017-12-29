// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.ResolveState
import org.jetbrains.plugins.groovy.lang.resolve.ElementGroovyResult
import org.jetbrains.plugins.groovy.lang.resolve.GrResolverProcessor

class TypeParameterProcessor(name: String) : GrScopeProcessorWithHints(name, emptySet()),
                                             GrResolverProcessor<ElementGroovyResult<PsiTypeParameter>>,
                                             GroovyResolveKind.Hint {

  private var myResult: ElementGroovyResult<PsiTypeParameter>? = null
  override val results: List<ElementGroovyResult<PsiTypeParameter>>
    get() = myResult.let { result ->
      if (result == null) emptyList() else listOf(result)
    }

  override fun execute(element: PsiElement, state: ResolveState): Boolean {
    element as? PsiTypeParameter ?: return true
    myResult = ElementGroovyResult(element)
    return false
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any?> getHint(hintKey: Key<T>): T? {
    return when (hintKey) {
      GroovyResolveKind.HINT_KEY -> this as T
      else -> super<GrScopeProcessorWithHints>.getHint(hintKey)
    }
  }

  override fun shouldProcess(kind: GroovyResolveKind): Boolean = kind == GroovyResolveKind.TYPE_PARAMETER
}
