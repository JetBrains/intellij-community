// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable
import org.jetbrains.plugins.groovy.lang.resolve.CompilationPhaseHint
import org.jetbrains.plugins.groovy.lang.resolve.ElementResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.DECLARATION_SCOPE_PASSED

class DuplicateVariableProcessor(private val variable: GrVariable) : FindFirstProcessor<ElementResolveResult<GrVariable>>() {

  companion object {
    private fun GrVariable.hasExplicitVisibilityModifiers(): Boolean = modifierList?.hasExplicitVisibilityModifiers() ?: false
  }

  private val name = variable.name

  init {
    nameHint(name)
    hint(CompilationPhaseHint.HINT_KEY, CompilationPhaseHint { CompilationPhaseHint.Phase.CONVERSION })
  }

  private val hasVisibilityModifier = variable.hasExplicitVisibilityModifiers()

  override fun result(element: PsiElement, state: ResolveState): ElementResolveResult<GrVariable>? {
    if (element !is GrVariable || element is GrBindingVariable) return null
    if (element == variable) return null
    if (element.hasExplicitVisibilityModifiers() != hasVisibilityModifier) return null
    return ElementResolveResult(element)
  }

  private var myBorderPassed: Boolean = false

  override fun handleEvent(event: PsiScopeProcessor.Event, associated: Any?) {
    if (event != DECLARATION_SCOPE_PASSED || associated !is PsiElement) return
    if (associated is GrClosableBlock && GrClosableBlock.OWNER_NAME == name ||
        associated is PsiClass && associated !is PsiAnonymousClass ||
        associated is GrMethod && associated.parent is GroovyFile) {
      myBorderPassed = true
    }
  }

  override fun shouldStop(): Boolean = myBorderPassed
}
