package com.intellij.webSymbols.documentation.impl

import com.intellij.webSymbols.WebSymbolApiStatus
import com.intellij.webSymbols.documentation.WebSymbolDocumentation
import org.jetbrains.annotations.Nls
import javax.swing.Icon

internal data class WebSymbolDocumentationImpl(override val name: String,
                                               override val definition: String,
                                               override val definitionDetails: String?,
                                               override val description: @Nls String?,
                                               override val docUrl: String?,
                                               override val apiStatus: WebSymbolApiStatus?,
                                               override val required: Boolean,
                                               override val defaultValue: String?,
                                               override val library: String?,
                                               override val icon: Icon?,
                                               override val descriptionSections: Map<@Nls String, @Nls String>,
                                               override val footnote: @Nls String?,
                                               override val header: @Nls String?) : WebSymbolDocumentation {
  override fun isNotEmpty(): Boolean =
    name != definition || description != null || docUrl != null || (apiStatus != null && apiStatus != WebSymbolApiStatus.Stable)
    || required || defaultValue != null || library != null || descriptionSections.isNotEmpty() || footnote != null
    || header != null

  override fun withName(name: String): WebSymbolDocumentation =
    copy(name = name)

  override fun withDefinition(definition: String): WebSymbolDocumentation =
    copy(definition = definition)

  override fun withDefinitionDetails(definitionDetails: String?): WebSymbolDocumentation =
    copy(definitionDetails = definitionDetails)

  override fun withDescription(description: @Nls String?): WebSymbolDocumentation =
    copy(description = description)

  override fun withDocUrl(docUrl: String?): WebSymbolDocumentation =
    copy(docUrl = docUrl)

  override fun withApiStatus(apiStatus: WebSymbolApiStatus?): WebSymbolDocumentation =
    copy(apiStatus = apiStatus)

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

  override fun withHeader(header: @Nls String?): WebSymbolDocumentation =
    copy(header = header)

  override fun with(name: String,
                    definition: String,
                    definitionDetails: String?,
                    description: @Nls String?,
                    docUrl: String?,
                    apiStatus: WebSymbolApiStatus?,
                    required: Boolean,
                    defaultValue: String?,
                    library: String?,
                    icon: Icon?,
                    additionalSections: Map<@Nls String, @Nls String>,
                    footnote: @Nls String?): WebSymbolDocumentation =
    copy(name = name, definition = definition, definitionDetails = definitionDetails, description = description,
         docUrl = docUrl, apiStatus = apiStatus, required = required, defaultValue = defaultValue, library = library, icon = icon,
         descriptionSections = descriptionSections + additionalSections, footnote = footnote)

}