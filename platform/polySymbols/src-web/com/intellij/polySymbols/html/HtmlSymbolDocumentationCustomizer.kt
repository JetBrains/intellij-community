// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.PolySymbolsBundle
import com.intellij.polySymbols.documentation.PolySymbolDocumentation
import com.intellij.polySymbols.documentation.PolySymbolDocumentationCustomizer
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class HtmlSymbolDocumentationCustomizer : PolySymbolDocumentationCustomizer {
  override fun customize(symbol: PolySymbol, location: PsiElement?, documentation: PolySymbolDocumentation): PolySymbolDocumentation {
    if (symbol.qualifiedKind.namespace != NAMESPACE_HTML) return documentation
    if (symbol.modifiers.contains(PolySymbolModifier.REQUIRED))
      return documentation.withDescriptionSection(PolySymbolsBundle.message("mdn.documentation.section.isRequired"), "")
    else
      return documentation
  }
}