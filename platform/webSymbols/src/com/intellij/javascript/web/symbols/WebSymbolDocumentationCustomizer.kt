package com.intellij.javascript.web.symbols

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface WebSymbolDocumentationCustomizer {

  fun customize(symbol: WebSymbol, documentation: WebSymbolDocumentation): WebSymbolDocumentation

  companion object {
    val EP_NAME = ExtensionPointName.create<WebSymbolDocumentationCustomizer>(
      "com.intellij.javascript.web.documentationCustomizer")
  }
}