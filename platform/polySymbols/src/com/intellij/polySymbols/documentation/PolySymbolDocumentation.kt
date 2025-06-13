// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.documentation

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.PolySymbolOrigin
import com.intellij.polySymbols.documentation.impl.PolySymbolDocumentationBuilderImpl
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

    @JvmStatic
    fun create(
      symbol: PolySymbolWithDocumentation,
      location: PsiElement?,
      builder: (PolySymbolDocumentationBuilder.() -> Unit),
    ): PolySymbolDocumentation =
      PolySymbolDocumentationBuilderImpl(symbol, location)
        .also { builder.invoke(it) }
        .build()

    @JvmStatic
    fun create(
      symbol: PolySymbolWithDocumentation,
      location: PsiElement?,
    ): PolySymbolDocumentationBuilder =
      PolySymbolDocumentationBuilderImpl(symbol, location)

  }

}
