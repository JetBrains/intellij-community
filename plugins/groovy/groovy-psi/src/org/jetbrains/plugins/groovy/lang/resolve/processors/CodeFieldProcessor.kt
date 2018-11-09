// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.ElementClassHint
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.resolve.BaseGroovyResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.CompilationPhaseHint
import org.jetbrains.plugins.groovy.lang.resolve.ElementResolveResult

class CodeFieldProcessor(name: String, private val place: PsiElement) : FindFirstProcessor<ElementResolveResult<GrField>>() {

  init {
    nameHint(name)
    hint(ElementClassHint.KEY, ClassHint.EMPTY)
    hint(GroovyResolveKind.HINT_KEY, GroovyResolveKind.Hint { it == GroovyResolveKind.FIELD })
    hint(CompilationPhaseHint.HINT_KEY, CompilationPhaseHint.BEFORE_TRANSFORMATION)
  }

  override fun result(element: PsiElement, state: ResolveState): ElementResolveResult<GrField>? {
    val field = element as? GrField ?: return null
    return BaseGroovyResolveResult(field, place, state.get(ClassHint.RESOLVE_CONTEXT), state.get(PsiSubstitutor.KEY))
  }
}
