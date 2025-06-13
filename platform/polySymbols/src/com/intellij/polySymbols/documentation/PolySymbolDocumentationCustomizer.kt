// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.documentation

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.polySymbols.PolySymbol
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.TestOnly

interface PolySymbolDocumentationCustomizer {

  fun customize(symbol: PolySymbol, location: PsiElement?, documentation: PolySymbolDocumentation): PolySymbolDocumentation

  companion object {

    @TestOnly
    @JvmField
    val EP_NAME: ExtensionPointName<PolySymbolDocumentationCustomizer> = ExtensionPointName.create<PolySymbolDocumentationCustomizer>(
      "com.intellij.polySymbols.documentationCustomizer")
  }
}