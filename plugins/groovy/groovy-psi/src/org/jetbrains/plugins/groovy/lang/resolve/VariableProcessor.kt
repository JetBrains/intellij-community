/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.resolve

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
import org.jetbrains.plugins.groovy.lang.resolve.processors.DynamicMembersHint

class VariableProcessor(private val myName: String) : PsiScopeProcessor, NameHint, ElementClassHint, DynamicMembersHint {

  private var myStop: Boolean = false
  private var myResult: GroovyResolveResult? = null
  val result: GroovyResolveResult? get() = myResult

  override fun <T : Any?> getHint(hintKey: Key<T>): T? {
    if (hintKey == NameHint.KEY || hintKey == ElementClassHint.KEY || hintKey == DynamicMembersHint.KEY) {
      @Suppress("UNCHECKED_CAST")
      return this as? T
    }
    return null
  }

  override fun getName(state: ResolveState) = myName

  override fun shouldProcess(kind: ElementClassHint.DeclarationKind?) = !myStop && kind == VARIABLE

  override fun execute(element: PsiElement, state: ResolveState): Boolean {
    if (myStop) return false
    assert(element !is GrBindingVariable)
    if (element is GrVariable && element !is GrField) {
      myResult = GroovyResolveResultImpl(element, true)
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
    if (associated is GrMember || associated is GroovyFile) myStop = true
  }
}