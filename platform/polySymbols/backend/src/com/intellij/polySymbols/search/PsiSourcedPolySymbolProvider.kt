// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.search

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.TestOnly

/**
 * Implement this interface if symbol name can have a different format and a name provider needs to be
 * called to allow for [com.intellij.polySymbols.PolySymbol] -> [PsiElement] reference to be found.
 */
interface PsiSourcedPolySymbolProvider {

  fun getSymbols(element: PsiElement): List<PsiSourcedPolySymbol>

  @Suppress("TestOnlyProblems")
  companion object {
    @TestOnly
    @JvmField
    val EP_NAME: ExtensionPointName<PsiSourcedPolySymbolProvider> = ExtensionPointName.Companion.create<PsiSourcedPolySymbolProvider>("com.intellij.polySymbols.psiSourcedSymbolProvider")

    fun getAllSymbols(element: PsiElement): Collection<PsiSourcedPolySymbol> =
      EP_NAME.extensionList.flatMap { it.getSymbols(element) }

  }

}