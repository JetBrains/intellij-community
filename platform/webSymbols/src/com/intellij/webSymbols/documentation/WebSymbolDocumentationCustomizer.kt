// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.documentation

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.WebSymbol

interface WebSymbolDocumentationCustomizer {

  fun customize(symbol: WebSymbol, location: PsiElement?, documentation: WebSymbolDocumentation): WebSymbolDocumentation

  companion object {
    val EP_NAME: ExtensionPointName<WebSymbolDocumentationCustomizer> = ExtensionPointName.create<WebSymbolDocumentationCustomizer>(
      "com.intellij.webSymbols.documentationCustomizer")
  }
}