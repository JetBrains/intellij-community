// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.ElementClassHint
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.BaseGroovyResolveResult

/**
 * Processor that stops on the first found [PsiField].
 */
internal class FirstFieldProcessor(name: String, private val place: GroovyPsiElement) : FindFirstProcessor<GroovyResolveResult>() {

  init {
    nameHint(name)
    hint(ElementClassHint.KEY, ElementClassHint { it == ElementClassHint.DeclarationKind.FIELD })
  }

  override fun result(element: PsiElement, state: ResolveState): GroovyResolveResult? {
    val field = element as? PsiField ?: return null
    return BaseGroovyResolveResult(field, place, state)
  }
}
