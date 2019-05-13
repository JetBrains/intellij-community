// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.ProcessorWithHints

open class CollectElementsProcessor : ProcessorWithHints() {

  private val myResults = mutableListOf<PsiElement>()
  val results: List<PsiElement> get() = myResults

  final override fun execute(element: PsiElement, state: ResolveState): Boolean {
    myResults += element
    return true
  }
}
