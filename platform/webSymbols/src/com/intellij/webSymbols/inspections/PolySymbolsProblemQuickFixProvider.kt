// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.PolySymbol
import com.intellij.webSymbols.PolySymbolNameSegment
import com.intellij.webSymbols.references.WebSymbolReferenceProblem

interface PolySymbolsProblemQuickFixProvider {

  fun getQuickFixes(
    element: PsiElement,
    symbol: PolySymbol,
    segment: PolySymbolNameSegment,
    problemKind: WebSymbolReferenceProblem.ProblemKind,
  ): List<LocalQuickFix>

  companion object {
    val EP_NAME: ExtensionPointName<PolySymbolsProblemQuickFixProvider> =
      ExtensionPointName<PolySymbolsProblemQuickFixProvider>("com.intellij.webSymbols.problemQuickFixProvider")

    fun getQuickFixes(element: PsiElement, symbol: PolySymbol, segment: PolySymbolNameSegment, problemKind: WebSymbolReferenceProblem.ProblemKind): List<LocalQuickFix> =
      EP_NAME.extensionList.flatMap { it.getQuickFixes(element, symbol, segment, problemKind) }
  }
}