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
import com.intellij.util.asSafely
import org.jetbrains.annotations.Nls
import javax.swing.Icon

internal class PolySymbolDocumentationBuilderImpl(
  private val symbol: PolySymbol,
  private val location: PsiElement?,
) : PolySymbolDocumentationBuilder {
  private var name: String = symbol.name
  private var definition: String = Strings.escapeXmlEntities(symbol.name)
  private var definitionDetails: String? = null
  private var description: @Nls String? = null
  private var docUrl: String? = null
  private var apiStatus: PolySymbolApiStatus? = symbol.apiStatus
  private var defaultValue: String? = null
  private var library: String? = null
  private var icon: Icon? = symbol.icon?.takeIf { symbol[DocHideIconProperty] != true }
  private val descriptionSections: MutableMap<@Nls String, @Nls String> = mutableMapOf()
  private var footnote: @Nls String? = null
  private var header: @Nls String? = null
  private val iconProviders = mutableListOf<(String) -> Icon?>()

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

  override fun iconProvider(provider: (String) -> Icon?) {
    iconProviders.add(provider)
  }

  override fun copyFrom(other: PolySymbol?): Boolean {
    val target = other
      ?.getDocumentationTarget(location)
      ?.asSafely<PolySymbolDocumentationTargetImpl<PolySymbol>>()
    if (target == null) return false
    val defaultBuilder = PolySymbolDocumentationBuilderImpl(target.symbol, location)
    name = defaultBuilder.name
    definition = defaultBuilder.definition
    definitionDetails = defaultBuilder.definitionDetails
    description = defaultBuilder.description
    docUrl = defaultBuilder.docUrl
    apiStatus = defaultBuilder.apiStatus
    defaultValue = defaultBuilder.defaultValue
    library = defaultBuilder.library
    icon = defaultBuilder.icon
    descriptionSections.clear()
    descriptionSections.putAll(defaultBuilder.descriptionSections)
    footnote = defaultBuilder.footnote
    header = defaultBuilder.header
    iconProviders.clear()
    iconProviders.addAll(defaultBuilder.iconProviders)
    target.builder(this, target.symbol, location)
    return true
  }

  @Suppress("TestOnlyProblems")
  fun build(): PolySymbolDocumentation =
    PolySymbolDocumentationImpl(
      name, definition, definitionDetails, description, docUrl, apiStatus, defaultValue, library,
      icon, descriptionSections, footnote, header, iconProviders
    ).let { doc: PolySymbolDocumentation ->
      PolySymbolDocumentationCustomizer.EP_NAME.extensionList.fold(doc) { documentation, customizer ->
        customizer.customize(symbol, location, documentation)
      }
    }
}
