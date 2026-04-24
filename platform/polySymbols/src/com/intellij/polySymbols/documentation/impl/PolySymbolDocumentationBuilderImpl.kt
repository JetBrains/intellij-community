// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.documentation.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.Strings
import com.intellij.polySymbols.PolySymbol.DocHideIconProperty
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.documentation.PolySymbolDocumentation
import com.intellij.polySymbols.documentation.PolySymbolDocumentationBuilder
import com.intellij.polySymbols.documentation.PolySymbolDocumentationCustomizer
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import javax.swing.Icon

internal class PolySymbolDocumentationBuilderImpl(
  private val symbol: PolySymbol,
  private val location: PsiElement?,
) : PolySymbolDocumentationBuilder {
  override var name: String = symbol.name
  override var definition: String = Strings.escapeXmlEntities(symbol.name)
  override var definitionDetails: String? = null
  override var description: @Nls String? = null
  override var docUrl: String? = null
  override var apiStatus: PolySymbolApiStatus? = symbol.apiStatus
  override var defaultValue: String? = null
  override var library: String? = null
  override var icon: Icon? = symbol.icon?.takeIf { symbol[DocHideIconProperty] != true }
  override var descriptionSections: MutableMap<@Nls String, @Nls String> = mutableMapOf()
  override var footnote: @Nls String? = null
  override var header: @Nls String? = null

  override fun name(value: @NlsSafe String): PolySymbolDocumentationBuilder {
    name = value
    return this
  }

  override fun definition(value: @NlsSafe String): PolySymbolDocumentationBuilder {
    definition = value
    return this
  }

  override fun definitionDetails(value: @NlsSafe String?): PolySymbolDocumentationBuilder {
    definitionDetails = value
    return this
  }

  override fun description(value: @Nls String?): PolySymbolDocumentationBuilder {
    description = value
    return this
  }

  override fun docUrl(value: @NlsSafe String?): PolySymbolDocumentationBuilder {
    docUrl = value
    return this
  }

  override fun apiStatus(value: PolySymbolApiStatus?): PolySymbolDocumentationBuilder {
    apiStatus = value
    return this
  }

  override fun defaultValue(value: @NlsSafe String?): PolySymbolDocumentationBuilder {
    defaultValue = value
    return this
  }

  override fun library(value: @NlsSafe String?): PolySymbolDocumentationBuilder {
    library = value
    return this
  }

  override fun icon(value: Icon?): PolySymbolDocumentationBuilder {
    icon = value
    return this
  }

  override fun descriptionSection(name: @Nls String, contents: @Nls String): PolySymbolDocumentationBuilder {
    descriptionSections[name] = contents
    return this
  }

  override fun descriptionSections(sections: Map<@Nls String, @Nls String>): PolySymbolDocumentationBuilder {
    descriptionSections.putAll(sections)
    return this
  }

  override fun footnote(value: @Nls String?): PolySymbolDocumentationBuilder {
    footnote = value
    return this
  }

  override fun header(value: @Nls String?): PolySymbolDocumentationBuilder {
    header = value
    return this
  }

  @Suppress("TestOnlyProblems")
  override fun build(): PolySymbolDocumentation =
    PolySymbolDocumentationImpl(
      name, definition, definitionDetails, description, docUrl, apiStatus, defaultValue, library,
      icon, descriptionSections, footnote, header
    ).let { doc: PolySymbolDocumentation ->
      PolySymbolDocumentationCustomizer.EP_NAME.extensionList.fold(doc) { documentation, customizer ->
        customizer.customize(symbol, location, documentation)
      }
    }
}
