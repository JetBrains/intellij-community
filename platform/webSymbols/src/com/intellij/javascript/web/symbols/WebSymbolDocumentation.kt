// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.javascript.web.symbols

import com.intellij.javascript.web.symbols.impl.WebSymbolDocumentationImpl
import com.intellij.openapi.util.text.Strings
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

@ApiStatus.Experimental
/**
 * INAPPLICABLE_JVM_NAME -> https://youtrack.jetbrains.com/issue/KT-31420
 * DEPRECATION -> @JvmDefault
 **/
@Suppress("INAPPLICABLE_JVM_NAME", "DEPRECATION")
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

  @JvmDefault
  fun isNotEmpty(): Boolean =
    name != definition || description != null || docUrl != null || deprecated || experimental
    || required || defaultValue != null || library != null || descriptionSections.isNotEmpty() || footnote != null

  @JvmDefault
  fun withName(name: String): WebSymbolDocumentation =
    WebSymbolDocumentationImpl(name, definition, description, docUrl, deprecated, experimental,
                               required, defaultValue, library, icon, descriptionSections, footnote)

  @JvmDefault
  fun withDefinition(definition: String): WebSymbolDocumentation =
    WebSymbolDocumentationImpl(name, definition, description, docUrl, deprecated, experimental,
                               required, defaultValue, library, icon, descriptionSections, footnote)

  @JvmDefault
  fun withDescription(description: @Nls String?): WebSymbolDocumentation =
    WebSymbolDocumentationImpl(name, definition, description, docUrl, deprecated, experimental,
                               required, defaultValue, library, icon, descriptionSections, footnote)

  @JvmDefault
  fun withDocUrl(docUrl: String?): WebSymbolDocumentation =
    WebSymbolDocumentationImpl(name, definition, description, docUrl, deprecated, experimental,
                               required, defaultValue, library, icon, descriptionSections, footnote)

  @JvmDefault
  fun withDeprecated(deprecated: Boolean): WebSymbolDocumentation =
    WebSymbolDocumentationImpl(name, definition, description, docUrl, deprecated, experimental,
                               required, defaultValue, library, icon, descriptionSections, footnote)

  @JvmDefault
  fun withExperimental(experimental: Boolean): WebSymbolDocumentation =
    WebSymbolDocumentationImpl(name, definition, description, docUrl, deprecated, experimental,
                               required, defaultValue, library, icon, descriptionSections, footnote)

  @JvmDefault
  fun withRequired(required: Boolean): WebSymbolDocumentation =
    WebSymbolDocumentationImpl(name, definition, description, docUrl, deprecated, experimental,
                               required, defaultValue, library, icon, descriptionSections, footnote)

  @JvmDefault
  fun withDefault(defaultValue: String?): WebSymbolDocumentation =
    WebSymbolDocumentationImpl(name, definition, description, docUrl, deprecated, experimental,
                               required, defaultValue, library, icon, descriptionSections, footnote)

  @JvmDefault
  fun withLibrary(library: String?): WebSymbolDocumentation =
    WebSymbolDocumentationImpl(name, definition, description, docUrl, deprecated, experimental,
                               required, defaultValue, library, icon, descriptionSections, footnote)

  @JvmDefault
  fun withIcon(icon: Icon?): WebSymbolDocumentation =
    WebSymbolDocumentationImpl(name, definition, description, docUrl, deprecated, experimental,
                               required, defaultValue, library, icon, descriptionSections, footnote)

  @JvmDefault
  fun withDescriptionSection(@Nls name: String, @Nls contents: String): WebSymbolDocumentation =
    WebSymbolDocumentationImpl(name, definition, description, docUrl, deprecated, experimental,
                               required, defaultValue, library, icon, descriptionSections + Pair(name, contents), footnote)

  @JvmDefault
  fun withFootnote(@Nls footnote: String?): WebSymbolDocumentation =
    WebSymbolDocumentationImpl(name, definition, description, docUrl, deprecated, experimental,
                               required, defaultValue, library, icon, descriptionSections, footnote)

  @JvmDefault
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
           footnote: @Nls String? = this.footnote): WebSymbolDocumentation =
    WebSymbolDocumentationImpl(name, definition, description, docUrl, deprecated, experimental,
                               required, defaultValue, library, icon, descriptionSections + additionalSections, footnote)

  companion object {

    fun create(symbol: WebSymbol): WebSymbolDocumentation =
      WebSymbolDocumentationImpl(symbol.name, Strings.escapeXmlEntities(symbol.name), symbol.description, symbol.docUrl, symbol.deprecated,
                                 symbol.experimental,
                                 symbol.required ?: false,
                                 symbol.defaultValue ?: symbol.attributeValue?.default,
                                 symbol.origin.takeIf { it.packageName != null }
                                   ?.let { context ->
                                     context.packageName +
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
