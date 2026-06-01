// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.completion.impl

import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationSymbol
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.search.PsiLinkedPolySymbol
import com.intellij.polySymbols.utils.PolySymbolDelegate
import com.intellij.polySymbols.utils.PsiLinkedPolySymbolDelegate
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer

/**
 * We need to render documentation for lookup elements. Regular `PolySymbol` does not implement
 * `DocumentationSymbol` to have a context aware documentation, so the symbol needs to be wrapped
 * for code completion.
 */
class CodeCompletionPolySymbolWithDocumentation(
  override val delegate: PolySymbol,
  private val location: PsiElement,
) : PolySymbolDelegate<PolySymbol>, DocumentationSymbol {

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

class PsiLinkedCodeCompletionPolySymbolWithDocumentation(
  override val delegate: PsiLinkedPolySymbol,
  private val location: PsiElement,
) : PsiLinkedPolySymbolDelegate<PsiLinkedPolySymbol>, DocumentationSymbol {

  override fun createPointer(): Pointer<PsiLinkedCodeCompletionPolySymbolWithDocumentation> {
    val delegatePtr = delegate.createPointer()
    val locationPtr = location.createSmartPointer()
    return Pointer {
      val delegate = delegatePtr.dereference() ?: return@Pointer null
      val location = locationPtr.dereference() ?: return@Pointer null
      PsiLinkedCodeCompletionPolySymbolWithDocumentation(delegate, location)
    }
  }

  override fun getDocumentationTarget(): DocumentationTarget =
    delegate.getDocumentationTarget(location)
    ?: PolySymbolEmptyDocumentationTarget(delegate)

}

private class PolySymbolEmptyDocumentationTarget(private val symbol: PolySymbol) : DocumentationTarget {

  override fun computeDocumentation(): DocumentationResult? = null

  override fun computePresentation(): TargetPresentation =
    TargetPresentation.builder(symbol.name).icon(symbol.icon).presentation()

  override fun createPointer(): Pointer<out DocumentationTarget> {
    val delegatePtr = symbol.createPointer()
    return Pointer {
      val delegate = delegatePtr.dereference() ?: return@Pointer null
      PolySymbolEmptyDocumentationTarget(delegate)
    }
  }
}