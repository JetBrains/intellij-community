// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.ElementClassHint
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable
import org.jetbrains.plugins.groovy.lang.resolve.ElementResolveResult

class LocalVariableProcessor(name: String) : FindFirstProcessor<ElementResolveResult<GrVariable>>() {

  init {
    nameHint(name)
    hint(ElementClassHint.KEY, ClassHint.EMPTY)
    hint(GroovyResolveKind.HINT_KEY, GroovyResolveKind.Hint { it == GroovyResolveKind.VARIABLE })
  }

  override fun result(element: PsiElement, state: ResolveState): ElementResolveResult<GrVariable>? {
    if (element !is GrVariable || element is GrField) return null
    assert(element !is GrBindingVariable)
    return ElementResolveResult(element)
  }
}
