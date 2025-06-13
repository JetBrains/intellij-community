// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.documentation

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.Strings
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.PolySymbolOrigin
import com.intellij.polySymbols.documentation.impl.PolySymbolDocumentationImpl
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

/**
 * INAPPLICABLE_JVM_NAME -> https://youtrack.jetbrains.com/issue/KT-31420
 **/
@Suppress("INAPPLICABLE_JVM_NAME")
@ApiStatus.NonExtendable
interface PolySymbolDocumentation {

  /**
   * Symbol name
   */
  val name: @NlsSafe String

  /**
   * Symbol definition with HTML markup
   */
  val definition: @NlsSafe String

  /**
   * The rest of symbol definition with HTML markup,
   * if the whole definition is too long.
   */
  val definitionDetails: @NlsSafe String?

  /**
   * Description of the symbol with HTML markup
   */
  val description: @Nls String?

  /**
   * URL for external documentation
   */
  val docUrl: @NlsSafe String?

  /**
   * API status of the symbol - deprecated or experimental
   */
  val apiStatus: PolySymbolApiStatus?

  /**
   * Default value
   */
  val defaultValue: @NlsSafe String?

  /**
   * Library of origin
   */
  val library: @NlsSafe String?

  /**
   * Icon
   */
  val icon: Icon?

  /**
   * Custom sections to display in the documentation
   */
  val descriptionSections: Map<@Nls String, @Nls String>

  /**
   * Footnote shown after sections content
   */
  val footnote: @Nls String?

  /**
   * Header shown before definition
   */
  val header: @Nls String?

  fun isNotEmpty(): Boolean

  fun withName(name: @NlsSafe String): PolySymbolDocumentation

  fun withDefinition(definition: @NlsSafe String): PolySymbolDocumentation

  fun withDefinitionDetails(definitionDetails: @NlsSafe String?): PolySymbolDocumentation

  fun withDescription(description: @Nls String?): PolySymbolDocumentation

  fun withDocUrl(docUrl: @NlsSafe String?): PolySymbolDocumentation

  fun withApiStatus(apiStatus: PolySymbolApiStatus?): PolySymbolDocumentation

  fun withDefault(defaultValue: @NlsSafe String?): PolySymbolDocumentation

  fun withLibrary(library: @NlsSafe String?): PolySymbolDocumentation

  fun withIcon(icon: Icon?): PolySymbolDocumentation

  fun withDescriptionSection(name: @Nls String, contents: @Nls String): PolySymbolDocumentation

  fun withDescriptionSections(sections: Map<@Nls String, @Nls String>): PolySymbolDocumentation

  fun withFootnote(footnote: @Nls String?): PolySymbolDocumentation

  fun withHeader(header: @Nls String?): PolySymbolDocumentation

  fun appendFootnote(footnote: @Nls String?): PolySymbolDocumentation =
    if (footnote != null)
      withFootnote((this.footnote ?: "") + footnote)
    else
      this

  fun build(origin: PolySymbolOrigin): DocumentationResult

  companion object {

    fun create(
      symbol: PolySymbolWithDocumentation,
      location: PsiElement?,
      name: String = symbol.name,
      definition: String = Strings.escapeXmlEntities(symbol.name),
      definitionDetails: String? = null,
      description: @Nls String? = symbol.description,
      docUrl: String? = symbol.docUrl,
      apiStatus: PolySymbolApiStatus? = symbol.apiStatus,
      defaultValue: String? = symbol.defaultValue,
      library: String? = symbol.origin.takeIf { it.library != null }
        ?.let { context ->
          context.library +
          if (context.version?.takeIf { it != "0.0.0" } != null) "@${context.version}" else ""
        },
      icon: Icon? = symbol.icon,
      descriptionSections: Map<@Nls String, @Nls String> = symbol.descriptionSections,
      footnote: @Nls String? = null,
    ): PolySymbolDocumentation =
      PolySymbolDocumentationImpl(name, definition, definitionDetails, description, docUrl, apiStatus, defaultValue, library, icon,
                                  descriptionSections, footnote, null)
        .let { doc: PolySymbolDocumentation ->
          PolySymbolDocumentationCustomizer.EP_NAME.extensionList.fold(doc) { documentation, customizer ->
            customizer.customize(symbol, location, documentation)
          }
        }

  }

}
