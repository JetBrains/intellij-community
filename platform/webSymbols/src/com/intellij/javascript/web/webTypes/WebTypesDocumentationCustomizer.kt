package com.intellij.javascript.web.webTypes

import com.intellij.javascript.web.symbols.WebSymbolDocumentation
import com.intellij.javascript.web.symbols.WebSymbolDocumentationCustomizer
import com.intellij.javascript.web.symbols.WebSymbol
import com.intellij.javascript.web.symbols.patterns.RegExpPattern

class WebTypesDocumentationCustomizer: WebSymbolDocumentationCustomizer {
  override fun customize(symbol: WebSymbol, documentation: WebSymbolDocumentation): WebSymbolDocumentation {
    val pattern = symbol.pattern as? RegExpPattern
    return if (pattern != null && symbol.properties[WebSymbol.PROP_DOC_HIDE_PATTERN] != true) {
      documentation.withDescriptionSection("Pattern", pattern.toString())
    } else documentation
  }
}