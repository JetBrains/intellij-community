// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.navigation.NavigatableSymbol
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.webSymbols.documentation.WebSymbolDocumentation
import com.intellij.webSymbols.documentation.impl.WebSymbolDocumentationTargetImpl
import com.intellij.webSymbols.html.WebSymbolHtmlAttributeValue
import com.intellij.webSymbols.patterns.WebSymbolsPattern
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor
import com.intellij.webSymbols.utils.matchedNameOrName
import org.jetbrains.annotations.Nls
import java.util.*
import javax.swing.Icon

/*
 * INAPPLICABLE_JVM_NAME -> https://youtrack.jetbrains.com/issue/KT-31420
 **/
@Suppress("INAPPLICABLE_JVM_NAME")
interface WebSymbol : WebSymbolsScope, Symbol, NavigatableSymbol {

  val origin: WebSymbolOrigin

  val namespace: @NlsSafe SymbolNamespace

  val kind: @NlsSafe SymbolKind

  val name: @NlsSafe String

  val description: @Nls String?
    get() = null

  val descriptionSections: Map<@Nls String, @Nls String>
    get() = emptyMap()

  val docUrl: @NlsSafe String?
    get() = null

  val icon: Icon?
    get() = null

  val defaultValue: @NlsSafe String?
    get() = null

  val type: Any?
    get() = null

  @get:JvmName("isRequired")
  val required: Boolean?
    get() = null

  val apiStatus: ApiStatus?
    get() = null

  val attributeValue: WebSymbolHtmlAttributeValue?
    get() = null

  val pattern: WebSymbolsPattern?
    get() = null

  val queryScope: List<WebSymbolsScope>
    get() = listOf(this)

  @get:JvmName("isVirtual")
  val virtual: Boolean
    get() = false

  @get:JvmName("isAbstract")
  val abstract: Boolean
    get() = false

  @get:JvmName("isExtension")
  val extension: Boolean
    get() = false

  val priority: Priority?
    get() = null

  val proximity: Int?
    get() = null

  val psiContext: PsiElement?
    get() = null

  val properties: Map<String, Any>
    get() = emptyMap()

  @get:RequiresReadLock
  @get:RequiresBackgroundThread
  val presentation: TargetPresentation
    get() {
      // TODO use kind description provider
      val kindName = kind.replace('-', ' ').lowercase(Locale.US).let {
        when {
          it.endsWith("ies") -> it.substring(0, it.length - 3) + "y"
          it.endsWith("ses") -> it.substring(0, it.length - 2)
          it.endsWith("s") -> it.substring(0, it.length - 1)
          else -> it
        }
      }
      val description = "$namespace $kindName '$matchedNameOrName'"
      return TargetPresentation.builder(description)
        .icon(icon)
        .presentation()
    }

  fun getDocumentationTarget(location: PsiElement?): DocumentationTarget =
    WebSymbolDocumentationTargetImpl(this, location)

  fun createDocumentation(location: PsiElement?): WebSymbolDocumentation? =
    WebSymbolDocumentation.create(this, location)

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
    emptyList()

  override fun getModificationCount(): Long = 0

  override fun createPointer(): Pointer<out WebSymbol>

  fun isEquivalentTo(symbol: Symbol): Boolean =
    this == symbol

  fun adjustNameForRefactoring(queryExecutor: WebSymbolsQueryExecutor, newName: String, occurence: String): String =
    queryExecutor.namesProvider.adjustRename(namespace, kind, name, newName, occurence)

  sealed interface ApiStatus

  /**
   * @param message message with HTML markup
   */
  data class Deprecated(@Nls val message: String? = null) : ApiStatus

  /**
   * @param message message with HTML markup
   */
  data class Experimental(@Nls val message: String? = null) : ApiStatus

  enum class Priority(val value: Double) {
    LOWEST(0.0),
    LOW(1.0),
    NORMAL(10.0),
    HIGH(50.0),
    HIGHEST(100.0);
  }

  companion object {
    const val NAMESPACE_HTML = "html"
    const val NAMESPACE_CSS = "css"
    const val NAMESPACE_JS = "js"

    const val KIND_HTML_ELEMENTS = "elements"
    const val KIND_HTML_ATTRIBUTES = "attributes"
    const val KIND_HTML_ATTRIBUTE_VALUES = "values"
    const val KIND_HTML_SLOTS = "slots"

    const val KIND_CSS_PROPERTIES = "properties"
    const val KIND_CSS_PSEUDO_ELEMENTS = "pseudo-elements"
    const val KIND_CSS_PSEUDO_CLASSES = "pseudo-classes"
    const val KIND_CSS_FUNCTIONS = "functions"
    const val KIND_CSS_CLASSES = "classes"
    const val KIND_CSS_PARTS = "parts"

    const val KIND_JS_EVENTS = "events"
    const val KIND_JS_PROPERTIES = "properties"
    const val KIND_JS_STATIC_PROPERTIES = "static-properties"

    /** Specify language to inject in an HTML element */
    const val PROP_INJECT_LANGUAGE = "inject-language"

    /** Specify to hide pattern section in the documentation */
    const val PROP_DOC_HIDE_PATTERN = "doc-hide-pattern"

    /** Specify to hide item from code completion */
    const val PROP_HIDE_FROM_COMPLETION = "hide-from-completion"

    /**
     * Name of boolean property used by pseudo-elements and pseudo-classes
     * to specify whether they require arguments. Defaults to false.
     **/
    const val PROP_ARGUMENTS = "arguments"
  }
}