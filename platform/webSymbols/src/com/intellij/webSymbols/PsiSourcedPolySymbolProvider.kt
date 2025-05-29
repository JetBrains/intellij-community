// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement

/**
 * Implement this interface if symbol name can have a different format and a name provider needs to be
 * called to allow for [PolySymbol] -> [PsiElement] reference to be found.
 */
interface PsiSourcedPolySymbolProvider {

  fun getSymbols(element: PsiElement): List<PsiSourcedPolySymbol>

  companion object {
    val EP_NAME: ExtensionPointName<PsiSourcedPolySymbolProvider> = ExtensionPointName.create<PsiSourcedPolySymbolProvider>("com.intellij.webSymbols.psiSourcedSymbolProvider")

    fun getAllSymbols(element: PsiElement): Collection<PsiSourcedPolySymbol> =
      EP_NAME.extensionList.flatMap { it.getSymbols(element) }

  }

}