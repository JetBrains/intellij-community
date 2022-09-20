package com.intellij.webSymbols.impl

import com.intellij.webSymbols.WebSymbolDeclarationProvider
import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolDeclarationProvider
import com.intellij.psi.PsiElement

class WebSymbolDeclarationProviderDelegate : PsiSymbolDeclarationProvider {
  override fun getDeclarations(element: PsiElement, offsetInElement: Int): Collection<PsiSymbolDeclaration> =
    WebSymbolDeclarationProvider.getAllDeclarations(element, offsetInElement)
}