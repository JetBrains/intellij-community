// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.declarations

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.polySymbols.PolySymbol
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.TestOnly

interface PolySymbolDeclarationProvider {
  /**
   * If `offsetInElement < 0` provide all declarations in the element,
   * otherwise try to provide only those at the hinted offset. Declarations outside the offset
   * will be filtered out anyway.
   */
  fun getDeclarations(element: PsiElement, offsetInElement: Int): Collection<PolySymbolDeclaration>

  fun getEquivalentDeclarations(element: PsiElement, offsetInElement: Int, target: PolySymbol): Collection<PolySymbolDeclaration> =
    getDeclarations(element, offsetInElement)
      .filter { it.symbol.isEquivalentTo(target) }

  @Suppress("TestOnlyProblems")
  companion object {
    @TestOnly
    @JvmField
    val EP_NAME: ExtensionPointName<PolySymbolDeclarationProvider> =
      ExtensionPointName<PolySymbolDeclarationProvider>("com.intellij.polySymbols.declarationProvider")

    @JvmStatic
    fun getAllEquivalentDeclarations(element: PsiElement, offsetInElement: Int, target: PolySymbol): Collection<PolySymbolDeclaration> {
      return EP_NAME.extensionList.flatMap { it.getEquivalentDeclarations(element, offsetInElement, target) }
    }

    @JvmStatic
    fun getAllDeclarations(element: PsiElement, offsetInElement: Int): Collection<PolySymbolDeclaration> {
      return EP_NAME.extensionList.flatMap { it.getDeclarations(element, offsetInElement) }
    }
  }
}