// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolNameSegment
import com.intellij.polySymbols.references.PolySymbolReferenceProblem.ProblemKind
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.TestOnly

interface PolySymbolProblemQuickFixProvider {

  fun getQuickFixes(
    element: PsiElement,
    symbol: PolySymbol,
    segment: PolySymbolNameSegment,
    problemKind: ProblemKind,
  ): List<LocalQuickFix>

  companion object {

    @TestOnly
    @JvmField
    val EP_NAME: ExtensionPointName<PolySymbolProblemQuickFixProvider> =
      ExtensionPointName<PolySymbolProblemQuickFixProvider>("com.intellij.polySymbols.problemQuickFixProvider")

    @Suppress("TestOnlyProblems")
    fun getQuickFixes(element: PsiElement, symbol: PolySymbol, segment: PolySymbolNameSegment, problemKind: ProblemKind): List<LocalQuickFix> =
      EP_NAME.extensionList.flatMap { it.getQuickFixes(element, symbol, segment, problemKind) }
  }
}