// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.propertyBased

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser.psiTraverser
import org.jetbrains.jetCheck.ImperativeCommand.Environment

/**
 * Checks that all references on elements don't throw exceptions when being resolved.
 */
class ResolveAllReferences(file: PsiFile) : ActionOnFile(file) {

  override fun performCommand(env: Environment) {
    for (element in psiTraverser(file)) {
      resolveAllReferences(element)
    }
  }

  private fun resolveAllReferences(element: PsiElement) {
    for (reference in element.references) {
      try {
        reference.resolve()
      }
      catch (e: Throwable) {
        throw RuntimeException("Element: ${element.javaClass}; ref: ${reference.javaClass}", e)
      }
    }
  }
}
