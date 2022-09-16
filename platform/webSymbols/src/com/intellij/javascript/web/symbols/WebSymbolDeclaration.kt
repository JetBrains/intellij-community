package com.intellij.javascript.web.symbols

import com.intellij.model.psi.PsiSymbolDeclaration

interface WebSymbolDeclaration: PsiSymbolDeclaration {

  override fun getSymbol(): WebSymbol

}