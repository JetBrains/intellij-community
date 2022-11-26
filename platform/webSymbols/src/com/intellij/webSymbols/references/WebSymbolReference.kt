// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.references

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.webSymbols.WebSymbol

interface WebSymbolReference : PsiSymbolReference {

  override fun resolveReference(): Collection<WebSymbol>

  override fun resolvesTo(target: Symbol): Boolean =
    resolveReference().any { it.isEquivalentTo(target) }

  fun getProblems(): Collection<WebSymbolReferenceProblem> =
    emptyList()

}