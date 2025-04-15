// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.declarations

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.WebSymbol

interface WebSymbolDeclarationProvider {
  /**
   * If `offsetInElement < 0` provide all declarations in the element,
   * otherwise try to provide only those at the hinted offset. Declarations outside the offset
   * will be filtered out anyway.
   */
  fun getDeclarations(element: PsiElement, offsetInElement: Int): Collection<WebSymbolDeclaration>

  fun getEquivalentDeclarations(element: PsiElement, offsetInElement: Int, target: WebSymbol): Collection<WebSymbolDeclaration> =
    getDeclarations(element, offsetInElement)
      .filter { it.symbol.isEquivalentTo(target) }

  companion object {
    private val EP_NAME = ExtensionPointName<WebSymbolDeclarationProvider>("com.intellij.webSymbols.declarationProvider")

    @JvmStatic
    fun getAllEquivalentDeclarations(element: PsiElement, offsetInElement: Int, target: WebSymbol): Collection<WebSymbolDeclaration> {
      return EP_NAME.extensionList.flatMap { it.getEquivalentDeclarations(element, offsetInElement, target) }
    }

    @JvmStatic
    fun getAllDeclarations(element: PsiElement, offsetInElement: Int): Collection<WebSymbolDeclaration> {
      return EP_NAME.extensionList.flatMap { it.getDeclarations(element, offsetInElement) }
    }
  }
}