// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GrTryResourceList
import org.jetbrains.plugins.groovy.lang.psi.util.backwardSiblings

class GrTryResourceListImpl(node: ASTNode) : GroovyPsiElementImpl(node), GrTryResourceList {

  override fun accept(visitor: GroovyElementVisitor): Unit = visitor.visitTryResourceList(this)

  override fun processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement?, place: PsiElement): Boolean {
    val startElement = lastParent ?: lastChild
    for (element in startElement.backwardSiblings()) {
      if (!element.processDeclarations(processor, state, null, place)) return false
    }
    return true
  }

  override fun toString(): String = "Try resource list"
}
