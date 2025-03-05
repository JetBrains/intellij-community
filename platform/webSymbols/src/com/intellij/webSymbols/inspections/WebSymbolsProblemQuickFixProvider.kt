// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolNameSegment
import com.intellij.webSymbols.references.WebSymbolReferenceProblem

interface WebSymbolsProblemQuickFixProvider {

  fun getQuickFixes(
    element: PsiElement,
    symbol: WebSymbol,
    segment: WebSymbolNameSegment,
    problemKind: WebSymbolReferenceProblem.ProblemKind,
  ): List<LocalQuickFix>

  companion object {
    val EP_NAME: ExtensionPointName<WebSymbolsProblemQuickFixProvider> =
      ExtensionPointName<WebSymbolsProblemQuickFixProvider>("com.intellij.webSymbols.problemQuickFixProvider")

    fun getQuickFixes(element: PsiElement, symbol: WebSymbol, segment: WebSymbolNameSegment, problemKind: WebSymbolReferenceProblem.ProblemKind): List<LocalQuickFix> =
      EP_NAME.extensionList.flatMap { it.getQuickFixes(element, symbol, segment, problemKind) }
  }
}