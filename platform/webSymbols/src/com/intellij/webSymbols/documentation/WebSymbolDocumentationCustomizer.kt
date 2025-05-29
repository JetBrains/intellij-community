// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.documentation

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.PolySymbol

interface WebSymbolDocumentationCustomizer {

  fun customize(symbol: PolySymbol, location: PsiElement?, documentation: PolySymbolDocumentation): PolySymbolDocumentation

  companion object {
    val EP_NAME: ExtensionPointName<WebSymbolDocumentationCustomizer> = ExtensionPointName.create<WebSymbolDocumentationCustomizer>(
      "com.intellij.webSymbols.documentationCustomizer")
  }
}