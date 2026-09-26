// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.documentation

import com.intellij.openapi.util.NlsSafe
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.PolySymbolDsl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

@DslMarker
annotation class PolySymbolDocumentationDsl

@PolySymbolDocumentationDsl
@PolySymbolDsl
@ApiStatus.NonExtendable
interface PolySymbolDocumentationBuilder {
  fun name(value: @NlsSafe String): PolySymbolDocumentationBuilder

  fun definition(value: @NlsSafe String): PolySymbolDocumentationBuilder

  fun definitionDetails(value: @NlsSafe String?): PolySymbolDocumentationBuilder

  fun description(value: @Nls String?): PolySymbolDocumentationBuilder

  fun docUrl(value: @NlsSafe String?): PolySymbolDocumentationBuilder

  fun apiStatus(value: PolySymbolApiStatus?): PolySymbolDocumentationBuilder

  fun defaultValue(value: @NlsSafe String?): PolySymbolDocumentationBuilder

  fun library(value: @NlsSafe String?): PolySymbolDocumentationBuilder

  fun icon(value: Icon?): PolySymbolDocumentationBuilder

  fun descriptionSection(name: @Nls String, contents: @Nls String): PolySymbolDocumentationBuilder
  fun descriptionSections(sections: Map<@Nls String, @Nls String>): PolySymbolDocumentationBuilder

  fun footnote(value: @Nls String?): PolySymbolDocumentationBuilder

  fun header(value: @Nls String?): PolySymbolDocumentationBuilder

  fun iconProvider(provider: (String) -> Icon?)

  fun copyFrom(other: PolySymbol?): Boolean
}
