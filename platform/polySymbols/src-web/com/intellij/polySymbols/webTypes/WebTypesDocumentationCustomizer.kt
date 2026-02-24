// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.webTypes

import com.intellij.polySymbols.PolySymbol.DocHidePatternProperty
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolsBundle
import com.intellij.polySymbols.documentation.PolySymbolDocumentation
import com.intellij.polySymbols.documentation.PolySymbolDocumentationCustomizer
import com.intellij.polySymbols.patterns.impl.RegExpPattern
import com.intellij.polySymbols.query.PolySymbolWithPattern
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
class WebTypesDocumentationCustomizer : PolySymbolDocumentationCustomizer {
  override fun customize(symbol: PolySymbol, location: PsiElement?, documentation: PolySymbolDocumentation): PolySymbolDocumentation {
    val pattern = (symbol as? PolySymbolWithPattern)?.pattern as? RegExpPattern
    return if (pattern != null && symbol[DocHidePatternProperty] != true) {
      @NonNls val patternString: String = pattern.toString()
      documentation.withDescriptionSection(PolySymbolsBundle.message("mdn.documentation.section.pattern"), patternString)
    }
    else documentation
  }
}