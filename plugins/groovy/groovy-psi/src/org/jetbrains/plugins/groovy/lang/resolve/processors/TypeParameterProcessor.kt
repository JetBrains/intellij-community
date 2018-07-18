// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.ElementClassHint
import org.jetbrains.plugins.groovy.lang.resolve.ElementGroovyResult

class TypeParameterProcessor(name: String) : FindFirstProcessor<ElementGroovyResult<PsiTypeParameter>>() {

  init {
    nameHint(name)
    hint(ElementClassHint.KEY, ClassHint.EMPTY)
    hint(GroovyResolveKind.HINT_KEY, GroovyResolveKind.Hint { it === GroovyResolveKind.TYPE_PARAMETER })
  }

  override fun result(element: PsiElement, state: ResolveState): ElementGroovyResult<PsiTypeParameter>? = (element as? PsiTypeParameter)?.let {
    ElementGroovyResult(it)
  }
}
