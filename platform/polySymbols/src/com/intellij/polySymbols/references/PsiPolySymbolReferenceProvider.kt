// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.references

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolNameSegment
import com.intellij.polySymbols.PolySymbolNameSegment.MatchProblem
import com.intellij.polySymbols.query.PolySymbolMatch

interface PsiPolySymbolReferenceProvider<T : PsiExternalReferenceHost> {

  fun getReferencedSymbol(psiElement: T): PolySymbol? = null

  fun getReferencedSymbolNameOffset(psiElement: T): Int = 0

  fun getOffsetsToReferencedSymbols(psiElement: T): Map<Int, PolySymbol> =
    getReferencedSymbol(psiElement)
      ?.let { mapOf(getReferencedSymbolNameOffset(psiElement) to it) }
    ?: emptyMap()

  fun shouldShowProblems(element: T): Boolean = true

  /**
   * Implement to optimize search usages. Quite often it is expensive
   * to resolve the reference, or to get the cache keys
   * (see [PsiPolySymbolReferenceCacheInfoProvider] implementations),
   * so it's better to return `false` if the provider can't possibly
   * reference the symbol, before cache keys are built and resolve is attempted.
   *
   * This method should be used with care because quite often it is challenging
   * to determine whether the symbol cannot be referenced by any symbol with a pattern.
   * Use it only when you do not expect symbols to have patterns, or you expect symbols
   * to refer to static definitions only, and you know the symbol should not refer to
   * a PSI element.
   *
   * @return `true` if the provider can possibly reference the symbol
   */
  fun canReference(target: Symbol): Boolean = true

  companion object {

    @JvmStatic
    fun unresolvedSymbol(kind: PolySymbolKind, name: String): PolySymbolMatch =
      PolySymbolMatch.create(
        name, kind, PolySymbolNameSegment.create(0, name.length, problem = MatchProblem.UNKNOWN_SYMBOL)
      )
  }

}