// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement

/**
 * Implement this interface if symbol name can have a different format and a name provider needs to be
 * called to allow for WebSymbol -> PsiElement reference to be found.
 */
interface PsiSourcedWebSymbolProvider {

  fun getWebSymbols(element: PsiElement): List<PsiSourcedWebSymbol>

  companion object {
    val EP_NAME = ExtensionPointName.create<PsiSourcedWebSymbolProvider>("com.intellij.webSymbols.psiSourcedSymbolProvider")

    fun getAllWebSymbols(element: PsiElement): Collection<PsiSourcedWebSymbol> =
      EP_NAME.extensions.asSequence().flatMap { it.getWebSymbols(element) }.toList()

  }

}