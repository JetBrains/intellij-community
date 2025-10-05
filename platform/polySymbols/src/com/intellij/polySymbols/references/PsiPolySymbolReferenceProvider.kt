// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.references

import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolNameSegment
import com.intellij.polySymbols.PolySymbolNameSegment.MatchProblem
import com.intellij.polySymbols.PolySymbolOrigin
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.query.PolySymbolMatch

interface PsiPolySymbolReferenceProvider<T : PsiExternalReferenceHost> {

  fun getReferencedSymbol(psiElement: T): PolySymbol? = null

  fun getReferencedSymbolNameOffset(psiElement: T): Int = 0

  fun getOffsetsToReferencedSymbols(psiElement: T): Map<Int, PolySymbol> =
    getReferencedSymbol(psiElement)
      ?.let { mapOf(getReferencedSymbolNameOffset(psiElement) to it) }
    ?: emptyMap()

  fun shouldShowProblems(element: T): Boolean = true

  companion object {

    @JvmStatic
    fun unresolvedSymbol(qualifiedKind: PolySymbolQualifiedKind, name: String, framework: String? = null): PolySymbolMatch =
      PolySymbolMatch.create(
        name, qualifiedKind, PolySymbolOrigin.create(framework),
        PolySymbolNameSegment.create(0, name.length, problem = MatchProblem.UNKNOWN_SYMBOL)
      )
  }

}