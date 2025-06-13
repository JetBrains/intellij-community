// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.documentation

import com.intellij.openapi.util.NlsSafe
import com.intellij.polySymbols.PolySymbolApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

interface PolySymbolDocumentationBuilder {
  fun name(value: @NlsSafe String): PolySymbolDocumentationBuilder
  val name: @NlsSafe String

  fun definition(value: @NlsSafe String): PolySymbolDocumentationBuilder
  val definition: @NlsSafe String

  fun definitionDetails(value: @NlsSafe String?): PolySymbolDocumentationBuilder
  val definitionDetails: @NlsSafe String?

  fun description(value: @Nls String?): PolySymbolDocumentationBuilder
  val description: @Nls String?

  fun docUrl(value: @NlsSafe String?): PolySymbolDocumentationBuilder
  val docUrl: @NlsSafe String?

  fun apiStatus(value: PolySymbolApiStatus?): PolySymbolDocumentationBuilder
  val apiStatus: PolySymbolApiStatus?

  fun defaultValue(value: @NlsSafe String?): PolySymbolDocumentationBuilder
  val defaultValue: @NlsSafe String?

  fun library(value: @NlsSafe String?): PolySymbolDocumentationBuilder
  val library: @NlsSafe String?

  fun icon(value: Icon?): PolySymbolDocumentationBuilder
  val icon: Icon?

  fun descriptionSection(name: @Nls String, contents: @Nls String): PolySymbolDocumentationBuilder
  fun descriptionSections(sections: Map<@Nls String, @Nls String>): PolySymbolDocumentationBuilder
  val descriptionSections: Map<@Nls String, @Nls String>

  fun footnote(value: @Nls String?): PolySymbolDocumentationBuilder
  val footnote: @Nls String?

  fun header(value: @Nls String?): PolySymbolDocumentationBuilder
  val header: @Nls String?

  fun build(): PolySymbolDocumentation
}
