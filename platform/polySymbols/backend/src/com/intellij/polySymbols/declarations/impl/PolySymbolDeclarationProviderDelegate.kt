// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.declarations.impl

import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolDeclarationProvider
import com.intellij.polySymbols.declarations.PolySymbolDeclarationProvider
import com.intellij.psi.PsiElement

class PolySymbolDeclarationProviderDelegate : PsiSymbolDeclarationProvider {
  override fun getDeclarations(element: PsiElement, offsetInElement: Int): Collection<PsiSymbolDeclaration> =
    PolySymbolDeclarationProvider.getAllDeclarations(element, offsetInElement)
}