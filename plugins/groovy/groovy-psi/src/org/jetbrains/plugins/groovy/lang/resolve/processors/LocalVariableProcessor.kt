// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.ElementClassHint.DeclarationKind.VARIABLE
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.DECLARATION_SCOPE_PASSED

class LocalVariableProcessor(private val name: String) : PsiScopeProcessor, NameHint, ElementClassHint {

  private var stopped: Boolean = false
  var resolveResult: GroovyResolveResult? = null
    private set

  override fun <T : Any?> getHint(hintKey: Key<T>): T? {
    if (hintKey == NameHint.KEY || hintKey == ElementClassHint.KEY) {
      @Suppress("UNCHECKED_CAST")
      return this as? T
    }
    return null
  }

  override fun getName(state: ResolveState) = name

  override fun shouldProcess(kind: ElementClassHint.DeclarationKind?) = !stopped && kind == VARIABLE

  override fun execute(element: PsiElement, state: ResolveState): Boolean {
    if (stopped) return false
    assert(element !is GrBindingVariable)
    if (element is GrVariable && element !is GrField) {
      resolveResult = GroovyResolveResultImpl(element, true)
      return false
    }
    else {
      return true
    }
  }

  /**
   * Stopped when first member is encountered, this member is a lexical scope.
   */
  override fun handleEvent(event: PsiScopeProcessor.Event, associated: Any?) {
    if (event != DECLARATION_SCOPE_PASSED) return
    if (associated is GrMember || associated is GroovyFile) stopped = true
  }
}