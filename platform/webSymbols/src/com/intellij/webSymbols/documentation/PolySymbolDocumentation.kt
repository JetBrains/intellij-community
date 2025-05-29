// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.documentation

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.Strings
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.PolySymbol
import com.intellij.webSymbols.PolySymbolApiStatus
import com.intellij.webSymbols.documentation.impl.PolySymbolDocumentationImpl
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
   * Whether the symbol is required
   */
  @get:JvmName("isRequired")
  val required: Boolean

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

  fun withRequired(required: Boolean): PolySymbolDocumentation

  fun withDefault(defaultValue: @NlsSafe String?): PolySymbolDocumentation

  fun withLibrary(library: @NlsSafe String?): PolySymbolDocumentation

  fun withIcon(icon: Icon?): PolySymbolDocumentation

  fun withDescriptionSection(name: @Nls String, contents: @Nls String): PolySymbolDocumentation

  fun withFootnote(footnote: @Nls String?): PolySymbolDocumentation

  fun withHeader(header: @Nls String?): PolySymbolDocumentation

  fun with(name: @NlsSafe String = this.name,
           definition: @NlsSafe String = this.definition,
           definitionDetails: @Nls String? = this.definitionDetails,
           description: @Nls String? = this.description,
           docUrl: @NlsSafe String? = this.docUrl,
           apiStatus: PolySymbolApiStatus? = this.apiStatus,
           required: Boolean = this.required,
           defaultValue: @NlsSafe String? = this.defaultValue,
           library: @NlsSafe String? = this.library,
           icon: Icon? = this.icon,
           additionalSections: Map<@Nls String, @Nls String> = emptyMap(),
           footnote: @Nls String? = this.footnote): PolySymbolDocumentation

  fun appendFootnote(footnote: @Nls String?): PolySymbolDocumentation =
    if (footnote != null)
      withFootnote((this.footnote ?: "") + footnote)
    else
      this

  companion object {

    fun create(symbol: PolySymbol,
               location: PsiElement?,
               name: String = symbol.name,
               definition: String = Strings.escapeXmlEntities(symbol.name),
               definitionDetails: String? = null,
               description: @Nls String? = symbol.description,
               docUrl: String? = symbol.docUrl,
               apiStatus: PolySymbolApiStatus? = symbol.apiStatus,
               required: Boolean = symbol.required ?: false,
               defaultValue: String? = symbol.defaultValue ?: symbol.attributeValue?.default,
               library: String? = symbol.origin.takeIf { it.library != null }
                 ?.let { context ->
                   context.library +
                   if (context.version?.takeIf { it != "0.0.0" } != null) "@${context.version}" else ""
                 },
               icon: Icon? = symbol.icon,
               descriptionSections: Map<@Nls String, @Nls String> = symbol.descriptionSections,
               footnote: @Nls String? = null): PolySymbolDocumentation =
      PolySymbolDocumentationImpl(name, definition, definitionDetails, description, docUrl, apiStatus, required, defaultValue, library, icon,
                                  descriptionSections, footnote, null)
        .let { doc: PolySymbolDocumentation ->
          WebSymbolDocumentationCustomizer.EP_NAME.extensionList.fold(doc) { documentation, customizer ->
            customizer.customize(symbol, location, documentation)
          }
        }

  }

}
