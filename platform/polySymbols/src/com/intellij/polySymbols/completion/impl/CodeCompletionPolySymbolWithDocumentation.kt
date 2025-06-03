// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.completion.impl

import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationSymbol
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.documentation.PolySymbolDocumentationTarget
import com.intellij.polySymbols.search.PsiSourcedPolySymbol
import com.intellij.polySymbols.utils.PolySymbolDelegate
import com.intellij.polySymbols.utils.PsiSourcedPolySymbolDelegate
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer

/**
 * We need to render documentation for lookup elements. Regular `PolySymbol` does not implement
 * `DocumentationSymbol` to have a context aware documentation, so the symbol needs to be wrapped
 * for code completion.
 */
class CodeCompletionPolySymbolWithDocumentation(
  delegate: PolySymbol,
  private val location: PsiElement,
) : PolySymbolDelegate<PolySymbol>(delegate), DocumentationSymbol {

  override fun createPointer(): Pointer<CodeCompletionPolySymbolWithDocumentation> {
    val delegatePtr = delegate.createPointer()
    val locationPtr = location.createSmartPointer()
    return Pointer {
      val delegate = delegatePtr.dereference() ?: return@Pointer null
      val location = locationPtr.dereference() ?: return@Pointer null
      CodeCompletionPolySymbolWithDocumentation(delegate, location)
    }
  }

  override fun getDocumentationTarget(): DocumentationTarget =
    delegate.getDocumentationTarget(location)
    ?: PolySymbolEmptyDocumentationTarget(delegate)

}

class PsiSourcedCodeCompletionPolySymbolWithDocumentation(
  delegate: PsiSourcedPolySymbol,
  private val location: PsiElement,
) : PsiSourcedPolySymbolDelegate<PsiSourcedPolySymbol>(delegate), DocumentationSymbol {

  override fun createPointer(): Pointer<PsiSourcedCodeCompletionPolySymbolWithDocumentation> {
    val delegatePtr = delegate.createPointer()
    val locationPtr = location.createSmartPointer()
    return Pointer {
      val delegate = delegatePtr.dereference() ?: return@Pointer null
      val location = locationPtr.dereference() ?: return@Pointer null
      PsiSourcedCodeCompletionPolySymbolWithDocumentation(delegate, location)
    }
  }

  override fun getDocumentationTarget(): DocumentationTarget =
    delegate.getDocumentationTarget(location)
    ?: PolySymbolEmptyDocumentationTarget(delegate)

}

private class PolySymbolEmptyDocumentationTarget(override val symbol: PolySymbol) : PolySymbolDocumentationTarget {

  override val location: PsiElement?
    get() = null

  override fun computeDocumentation(): DocumentationResult? = null

  override fun createPointer(): Pointer<out DocumentationTarget> {
    val delegatePtr = symbol.createPointer()
    return Pointer {
      val delegate = delegatePtr.dereference() ?: return@Pointer null
      PolySymbolEmptyDocumentationTarget(delegate)
    }
  }
}