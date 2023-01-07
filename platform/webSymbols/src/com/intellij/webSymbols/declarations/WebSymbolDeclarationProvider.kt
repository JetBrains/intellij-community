// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.declarations

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement

interface WebSymbolDeclarationProvider {

  /**
   * If `offsetInElement < 0` provide all declarations in the element,
   * otherwise try to provide only those at the hinted offset. Declarations outside the offset
   * will be filtered out anyway.
   */
  fun getDeclarations(element: PsiElement, offsetInElement: Int): Collection<WebSymbolDeclaration>

  companion object {
    private val EP_NAME = ExtensionPointName.create<WebSymbolDeclarationProvider>("com.intellij.webSymbols.declarationProvider")

    @JvmStatic
    fun getAllDeclarations(element: PsiElement, offsetInElement: Int): Collection<WebSymbolDeclaration> =
      EP_NAME.extensions.asSequence().flatMap { it.getDeclarations(element, offsetInElement) }.toList()

  }

}