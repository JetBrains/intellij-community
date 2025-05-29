package com.intellij.webSymbols.documentation.impl

import com.intellij.webSymbols.PolySymbolApiStatus
import com.intellij.webSymbols.documentation.PolySymbolDocumentation
import org.jetbrains.annotations.Nls
import javax.swing.Icon

internal data class PolySymbolDocumentationImpl(override val name: String,
                                                override val definition: String,
                                                override val definitionDetails: String?,
                                                override val description: @Nls String?,
                                                override val docUrl: String?,
                                                override val apiStatus: PolySymbolApiStatus?,
                                                override val required: Boolean,
                                                override val defaultValue: String?,
                                                override val library: String?,
                                                override val icon: Icon?,
                                                override val descriptionSections: Map<@Nls String, @Nls String>,
                                                override val footnote: @Nls String?,
                                                override val header: @Nls String?) : PolySymbolDocumentation {
  override fun isNotEmpty(): Boolean =
    name != definition || description != null || docUrl != null || (apiStatus != null && apiStatus != PolySymbolApiStatus.Stable)
    || required || defaultValue != null || library != null || descriptionSections.isNotEmpty() || footnote != null
    || header != null

  override fun withName(name: String): PolySymbolDocumentation =
    copy(name = name)

  override fun withDefinition(definition: String): PolySymbolDocumentation =
    copy(definition = definition)

  override fun withDefinitionDetails(definitionDetails: String?): PolySymbolDocumentation =
    copy(definitionDetails = definitionDetails)

  override fun withDescription(description: @Nls String?): PolySymbolDocumentation =
    copy(description = description)

  override fun withDocUrl(docUrl: String?): PolySymbolDocumentation =
    copy(docUrl = docUrl)

  override fun withApiStatus(apiStatus: PolySymbolApiStatus?): PolySymbolDocumentation =
    copy(apiStatus = apiStatus)

  override fun withRequired(required: Boolean): PolySymbolDocumentation =
    copy(required = required)

  override fun withDefault(defaultValue: String?): PolySymbolDocumentation =
    copy(defaultValue = defaultValue)

  override fun withLibrary(library: String?): PolySymbolDocumentation =
    copy(library = library)

  override fun withIcon(icon: Icon?): PolySymbolDocumentation =
    copy(icon = icon)

  override fun withDescriptionSection(@Nls name: String, @Nls contents: String): PolySymbolDocumentation =
    copy(descriptionSections = descriptionSections + Pair(name, contents))

  override fun withFootnote(@Nls footnote: String?): PolySymbolDocumentation =
    copy(footnote = footnote)

  override fun withHeader(header: @Nls String?): PolySymbolDocumentation =
    copy(header = header)

  override fun with(name: String,
                    definition: String,
                    definitionDetails: String?,
                    description: @Nls String?,
                    docUrl: String?,
                    apiStatus: PolySymbolApiStatus?,
                    required: Boolean,
                    defaultValue: String?,
                    library: String?,
                    icon: Icon?,
                    additionalSections: Map<@Nls String, @Nls String>,
                    footnote: @Nls String?): PolySymbolDocumentation =
    copy(name = name, definition = definition, definitionDetails = definitionDetails, description = description,
         docUrl = docUrl, apiStatus = apiStatus, required = required, defaultValue = defaultValue, library = library, icon = icon,
         descriptionSections = descriptionSections + additionalSections, footnote = footnote)

}