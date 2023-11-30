// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.completion.impl

import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationSymbol
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.createSmartPointer
import com.intellij.webSymbols.PsiSourcedWebSymbol
import com.intellij.webSymbols.PsiSourcedWebSymbolDelegate

/**
 * We need to render documentation for lookup elements. Regular `WebSymbol` does not implement
 * `DocumentationSymbol` to have a context aware documentation, so the symbol needs to be wrapped
 * for code completion.
 */
class PsiSourcedCodeCompletionWebSymbolWithDocumentation(delegate: PsiSourcedWebSymbol, private val location: PsiElement)
  : PsiSourcedWebSymbolDelegate<PsiSourcedWebSymbol>(delegate), DocumentationSymbol {
  override fun createPointer(): Pointer<PsiSourcedCodeCompletionWebSymbolWithDocumentation> {
    val delegatePtr = delegate.createPointer()
    val locationPtr = location.createSmartPointer()
    return Pointer {
      val location = locationPtr.dereference() ?: return@Pointer null
      val delegate = delegatePtr.dereference() ?: return@Pointer null
      PsiSourcedCodeCompletionWebSymbolWithDocumentation(delegate, location)
    }
  }

  override fun getDocumentationTarget(): DocumentationTarget =
    delegate.getDocumentationTarget(location)
}