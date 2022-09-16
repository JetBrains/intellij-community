package com.intellij.javascript.web.symbols

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolReference

interface WebSymbolReference : PsiSymbolReference {

  override fun resolveReference(): Collection<WebSymbol>

  @JvmDefault
  override fun resolvesTo(target: Symbol): Boolean =
    resolveReference().any { it.isEquivalentTo(target) }

  @JvmDefault
  fun getProblems(): Collection<WebSymbolReferenceProblem> =
    emptyList()

}