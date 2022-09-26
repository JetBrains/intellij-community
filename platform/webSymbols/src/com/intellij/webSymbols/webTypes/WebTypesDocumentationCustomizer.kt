// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes

import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolDocumentation
import com.intellij.webSymbols.WebSymbolDocumentationCustomizer
import com.intellij.webSymbols.patterns.impl.RegExpPattern

class WebTypesDocumentationCustomizer : WebSymbolDocumentationCustomizer {
  override fun customize(symbol: WebSymbol, documentation: WebSymbolDocumentation): WebSymbolDocumentation {
    val pattern = symbol.pattern as? RegExpPattern
    return if (pattern != null && symbol.properties[WebSymbol.PROP_DOC_HIDE_PATTERN] != true) {
      documentation.withDescriptionSection("Pattern", pattern.toString())
    }
    else documentation
  }
}