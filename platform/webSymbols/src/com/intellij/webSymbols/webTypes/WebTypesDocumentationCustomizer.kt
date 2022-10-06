// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes

import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.documentation.WebSymbolDocumentation
import com.intellij.webSymbols.documentation.WebSymbolDocumentationCustomizer
import com.intellij.webSymbols.WebSymbolsBundle
import com.intellij.webSymbols.patterns.impl.RegExpPattern
import org.jetbrains.annotations.NonNls

class WebTypesDocumentationCustomizer : WebSymbolDocumentationCustomizer {
  override fun customize(symbol: WebSymbol, documentation: WebSymbolDocumentation): WebSymbolDocumentation {
    val pattern = symbol.pattern as? RegExpPattern
    return if (pattern != null && symbol.properties[WebSymbol.PROP_DOC_HIDE_PATTERN] != true) {
      @NonNls val patternString: String = pattern.toString()
      documentation.withDescriptionSection(WebSymbolsBundle.message("mdn.documentation.section.pattern"), patternString)
    }
    else documentation
  }
}