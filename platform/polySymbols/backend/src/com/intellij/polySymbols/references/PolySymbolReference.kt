// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.references

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.polySymbols.PolySymbol

interface PolySymbolReference : PsiSymbolReference {

  override fun resolveReference(): Collection<PolySymbol>

  override fun resolvesTo(target: Symbol): Boolean =
    resolveReference().any { it.isEquivalentTo(target) }

  fun getProblems(): Collection<PolySymbolReferenceProblem> =
    emptyList()

}