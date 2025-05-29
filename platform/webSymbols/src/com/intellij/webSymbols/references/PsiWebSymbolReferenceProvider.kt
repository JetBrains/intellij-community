// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.references

import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.webSymbols.PolySymbol
import com.intellij.webSymbols.WebSymbolNameSegment
import com.intellij.webSymbols.WebSymbolNameSegment.MatchProblem
import com.intellij.webSymbols.PolySymbolOrigin
import com.intellij.webSymbols.PolySymbolQualifiedKind
import com.intellij.webSymbols.query.PolySymbolMatch

interface PsiWebSymbolReferenceProvider<T : PsiExternalReferenceHost> {

  fun getReferencedSymbol(psiElement: T): PolySymbol? = null

  fun getReferencedSymbolNameOffset(psiElement: T): Int = 0

  fun getOffsetsToReferencedSymbols(psiElement: T, hints: PsiSymbolReferenceHints): Map<Int, PolySymbol> =
    getReferencedSymbol(psiElement)
      ?.let { mapOf(getReferencedSymbolNameOffset(psiElement) to it) }
    ?: emptyMap()

  fun shouldShowProblems(element: T): Boolean = true

  companion object {

    @JvmStatic
    fun unresolvedSymbol(qualifiedKind: PolySymbolQualifiedKind, name: String, framework: String? = null): PolySymbolMatch =
      PolySymbolMatch.create(
        name, qualifiedKind, PolySymbolOrigin.create(framework),
        WebSymbolNameSegment.create(0, name.length, problem = MatchProblem.UNKNOWN_SYMBOL)
      )
  }

}