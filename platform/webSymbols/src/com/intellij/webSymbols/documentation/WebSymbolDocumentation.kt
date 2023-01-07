// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.documentation

import com.intellij.openapi.util.text.Strings
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.documentation.impl.WebSymbolDocumentationImpl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

/**
 * INAPPLICABLE_JVM_NAME -> https://youtrack.jetbrains.com/issue/KT-31420
 **/
@Suppress("INAPPLICABLE_JVM_NAME")
@ApiStatus.NonExtendable
interface WebSymbolDocumentation {

  /**
   * Symbol name
   */
  val name: String

  /**
   * Symbol definition with HTML markup
   */
  val definition: String

  /**
   * Description of the symbol with HTML markup
   */
  val description: @Nls String?

  /**
   * URL for external documentation
   */
  val docUrl: String?

  /**
   * Whether the symbol is deprecated
   */
  @get:JvmName("isDeprecated")
  val deprecated: Boolean

  /**
   * Whether the symbol is an experimental technology
   */
  @get:JvmName("isExperimental")
  val experimental: Boolean

  /**
   * Whether the symbol is required
   */
  @get:JvmName("isRequired")
  val required: Boolean

  /**
   * Default value
   */
  val defaultValue: String?

  /**
   * Library of origin
   */
  val library: String?

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

  fun isNotEmpty(): Boolean

  fun withName(name: String): WebSymbolDocumentation

  fun withDefinition(definition: String): WebSymbolDocumentation

  fun withDescription(description: @Nls String?): WebSymbolDocumentation

  fun withDocUrl(docUrl: String?): WebSymbolDocumentation

  fun withDeprecated(deprecated: Boolean): WebSymbolDocumentation

  fun withExperimental(experimental: Boolean): WebSymbolDocumentation

  fun withRequired(required: Boolean): WebSymbolDocumentation

  fun withDefault(defaultValue: String?): WebSymbolDocumentation

  fun withLibrary(library: String?): WebSymbolDocumentation

  fun withIcon(icon: Icon?): WebSymbolDocumentation

  fun withDescriptionSection(@Nls name: String, @Nls contents: String): WebSymbolDocumentation

  fun withFootnote(@Nls footnote: String?): WebSymbolDocumentation

  fun with(name: String = this.name,
           definition: String = this.definition,
           description: @Nls String? = this.description,
           docUrl: String? = this.docUrl,
           deprecated: Boolean = this.deprecated,
           experimental: Boolean = this.experimental,
           required: Boolean = this.required,
           defaultValue: String? = this.defaultValue,
           library: String? = this.library,
           icon: Icon? = this.icon,
           additionalSections: Map<@Nls String, @Nls String> = emptyMap(),
           footnote: @Nls String? = this.footnote): WebSymbolDocumentation

  companion object {

    fun create(symbol: WebSymbol): WebSymbolDocumentation =
      WebSymbolDocumentationImpl(symbol.name, Strings.escapeXmlEntities(symbol.name), symbol.description, symbol.docUrl, symbol.deprecated,
                                 symbol.experimental,
                                 symbol.required ?: false,
                                 symbol.defaultValue ?: symbol.attributeValue?.default,
                                 symbol.origin.takeIf { it.library != null }
                                   ?.let { context ->
                                     context.library +
                                     if (context.version?.takeIf { it != "0.0.0" } != null) "@${context.version}" else ""
                                   },
                                 symbol.icon, symbol.descriptionSections, null)
        .let { doc: WebSymbolDocumentation ->
          WebSymbolDocumentationCustomizer.EP_NAME.extensionList.fold(doc) { documentation, customizer ->
            customizer.customize(symbol, documentation)
          }
        }

  }

}
