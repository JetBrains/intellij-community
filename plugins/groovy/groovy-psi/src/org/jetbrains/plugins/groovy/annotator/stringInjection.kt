// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection

/**
 * @return first leaf element that contains line feed ignoring inner string literals
 */
internal fun getLineFeed(injection: GrStringInjection): PsiElement? {
  val visitor = LFSearchVisitor()
  injection.accept(visitor)
  return visitor.result
}

private class LFSearchVisitor : PsiRecursiveElementWalkingVisitor() {

  var result: PsiElement? = null
    private set

  override fun visitElement(element: PsiElement) {
    if (element is LeafPsiElement && element.textContains('\n')) {
      result = element
      stopWalking()
      return
    }
    else if (element !is GrLiteral) {
      super.visitElement(element)
    }
  }
}
