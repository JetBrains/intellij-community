package com.intellij.webSymbols.documentation.impl

import com.intellij.webSymbols.documentation.WebSymbolDocumentation
import org.jetbrains.annotations.Nls
import javax.swing.Icon

internal data class WebSymbolDocumentationImpl(override val name: String,
                                               override val definition: String,
                                               override val description: @Nls String?,
                                               override val docUrl: String?,
                                               override val deprecated: Boolean,
                                               override val experimental: Boolean,
                                               override val required: Boolean,
                                               override val defaultValue: String?,
                                               override val library: String?,
                                               override val icon: Icon?,
                                               override val descriptionSections: Map<@Nls String, @Nls String>,
                                               override val footnote: @Nls String?) : WebSymbolDocumentation {
  override fun isNotEmpty(): Boolean =
    name != definition || description != null || docUrl != null || deprecated || experimental
    || required || defaultValue != null || library != null || descriptionSections.isNotEmpty() || footnote != null

  override fun withName(name: String): WebSymbolDocumentation =
    copy(name = name)

  override fun withDefinition(definition: String): WebSymbolDocumentation =
    copy(definition = definition)

  override fun withDescription(description: @Nls String?): WebSymbolDocumentation =
    copy(description = description)

  override fun withDocUrl(docUrl: String?): WebSymbolDocumentation =
    copy(docUrl = docUrl)

  override fun withDeprecated(deprecated: Boolean): WebSymbolDocumentation =
    copy(deprecated = deprecated)

  override fun withExperimental(experimental: Boolean): WebSymbolDocumentation =
    copy(experimental = experimental)

  override fun withRequired(required: Boolean): WebSymbolDocumentation =
    copy(required = required)

  override fun withDefault(defaultValue: String?): WebSymbolDocumentation =
    copy(defaultValue = defaultValue)

  override fun withLibrary(library: String?): WebSymbolDocumentation =
    copy(library = library)

  override fun withIcon(icon: Icon?): WebSymbolDocumentation =
    copy(icon = icon)

  override fun withDescriptionSection(@Nls name: String, @Nls contents: String): WebSymbolDocumentation =
    copy(descriptionSections = descriptionSections + Pair(name, contents))

  override fun withFootnote(@Nls footnote: String?): WebSymbolDocumentation =
    copy(footnote = footnote)

  override fun with(name: String,
                    definition: String,
                    description: @Nls String?,
                    docUrl: String?,
                    deprecated: Boolean,
                    experimental: Boolean,
                    required: Boolean,
                    defaultValue: String?,
                    library: String?,
                    icon: Icon?,
                    additionalSections: Map<@Nls String, @Nls String>,
                    footnote: @Nls String?): WebSymbolDocumentation =
    copy(name = name, definition = definition, description = description, docUrl = docUrl, deprecated = deprecated,
         experimental = experimental,
         required = required, defaultValue = defaultValue, library = library, icon = icon,
         descriptionSections = descriptionSections + additionalSections, footnote = footnote)

}