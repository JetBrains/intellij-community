// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

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