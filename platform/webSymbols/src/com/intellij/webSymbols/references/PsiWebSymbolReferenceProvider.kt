// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.references

import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolNameSegment
import com.intellij.webSymbols.WebSymbolNameSegment.MatchProblem
import com.intellij.webSymbols.WebSymbolOrigin
import com.intellij.webSymbols.WebSymbolQualifiedKind
import com.intellij.webSymbols.query.WebSymbolMatch

interface PsiWebSymbolReferenceProvider<T : PsiExternalReferenceHost> {

  fun getReferencedSymbol(psiElement: T): WebSymbol? = null

  fun getReferencedSymbolNameOffset(psiElement: T): Int = 0

  fun getOffsetsToReferencedSymbols(psiElement: T, hints: PsiSymbolReferenceHints): Map<Int, WebSymbol> =
    getReferencedSymbol(psiElement)
      ?.let { mapOf(getReferencedSymbolNameOffset(psiElement) to it) }
    ?: emptyMap()

  fun shouldShowProblems(element: T): Boolean = true

  companion object {

    @JvmStatic
    fun unresolvedSymbol(qualifiedKind: WebSymbolQualifiedKind, name: String, framework: String? = null): WebSymbolMatch =
      WebSymbolMatch.create(
        name, qualifiedKind, WebSymbolOrigin.create(framework),
        WebSymbolNameSegment.create(0, name.length, problem = MatchProblem.UNKNOWN_SYMBOL)
      )
  }

}