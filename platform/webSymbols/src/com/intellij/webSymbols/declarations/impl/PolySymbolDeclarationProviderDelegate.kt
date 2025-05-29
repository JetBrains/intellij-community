package com.intellij.webSymbols.declarations.impl

import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolDeclarationProvider
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.declarations.PolySymbolDeclarationProvider

class PolySymbolDeclarationProviderDelegate : PsiSymbolDeclarationProvider {
  override fun getDeclarations(element: PsiElement, offsetInElement: Int): Collection<PsiSymbolDeclaration> =
    PolySymbolDeclarationProvider.getAllDeclarations(element, offsetInElement)
}