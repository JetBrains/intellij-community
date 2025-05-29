// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes

import com.intellij.psi.PsiElement
import com.intellij.webSymbols.PolySymbol
import com.intellij.webSymbols.PolySymbolsBundle
import com.intellij.webSymbols.documentation.PolySymbolDocumentation
import com.intellij.webSymbols.documentation.WebSymbolDocumentationCustomizer
import com.intellij.webSymbols.patterns.impl.RegExpPattern
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
class WebTypesDocumentationCustomizer : WebSymbolDocumentationCustomizer {
  override fun customize(symbol: PolySymbol, location: PsiElement?, documentation: PolySymbolDocumentation): PolySymbolDocumentation {
    val pattern = symbol.pattern as? RegExpPattern
    return if (pattern != null && symbol.properties[PolySymbol.PROP_DOC_HIDE_PATTERN] != true) {
      @NonNls val patternString: String = pattern.toString()
      documentation.withDescriptionSection(PolySymbolsBundle.message("mdn.documentation.section.pattern"), patternString)
    }
    else documentation
  }
}