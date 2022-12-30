// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.lang.documentation.symbol.DocumentationSymbol
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.presentation.PresentableSymbol
import com.intellij.model.presentation.SymbolPresentation
import com.intellij.navigation.NavigatableSymbol
import com.intellij.navigation.NavigationTarget
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.impl.WebSymbolDocumentationTargetImpl
import com.intellij.webSymbols.patterns.WebSymbolsPattern
import java.util.*
import javax.swing.Icon

/*
 * INAPPLICABLE_JVM_NAME -> https://youtrack.jetbrains.com/issue/KT-31420
 * DEPRECATION -> @JvmDefault
 **/
@Suppress("INAPPLICABLE_JVM_NAME", "DEPRECATION")
interface WebSymbol : WebSymbolsContainer, Symbol, PresentableSymbol, DocumentationSymbol, NavigatableSymbol {

  val origin: WebSymbolOrigin

  val namespace: SymbolNamespace

  val kind: SymbolKind

  @JvmDefault
  val matchedName: String
    get() = ""

  @JvmDefault
  override fun getModificationCount(): Long = 0

  override fun createPointer(): Pointer<out WebSymbol>

  @JvmDefault
  val psiContext: PsiElement?
    get() = null

  @JvmDefault
  @get:JvmName("isCompleteMatch")
  val completeMatch: Boolean
    get() = true

  @JvmDefault
  val nameSegments: List<WebSymbolNameSegment>
    get() = listOf(WebSymbolNameSegment(0, matchedName.length, this))

  @JvmDefault
  val contextContainers: Sequence<WebSymbolsContainer>
    get() = sequenceOf(this)

  @JvmDefault
  @get:NlsSafe
  val name: String
    get() = matchedName

  @JvmDefault
  val description: String?
    get() = null

  @JvmDefault
  val descriptionSections: Map<String, String>
    get() = emptyMap()

  @JvmDefault
  val docUrl: String?
    get() = null

  @JvmDefault
  val icon: Icon?
    get() = null

  @JvmDefault
  @get:JvmName("isDeprecated")
  val deprecated: Boolean
    get() = false

  @JvmDefault
  @get:JvmName("isExperimental")
  val experimental: Boolean
    get() = false

  @JvmDefault
  @get:JvmName("isVirtual")
  val virtual: Boolean
    get() = false

  @JvmDefault
  @get:JvmName("isAbstract")
  val abstract: Boolean
    get() = false

  @JvmDefault
  @get:JvmName("isExtension")
  val extension: Boolean
    get() = false

  @JvmDefault
  @get:JvmName("isRequired")
  val required: Boolean?
    get() = null

  @JvmDefault
  val defaultValue: String?
    get() = null

  @JvmDefault
  val priority: Priority?
    get() = null

  @JvmDefault
  val proximity: Int?
    get() = null

  @JvmDefault
  val type: Any?
    get() = null

  @JvmDefault
  val attributeValue: WebSymbolHtmlAttributeValue?
    get() = null

  @JvmDefault
  val pattern: WebSymbolsPattern?
    get() = null

  @JvmDefault
  val properties: Map<String, Any>
    get() = emptyMap()

  @JvmDefault
  val documentation: WebSymbolDocumentation?
    get() = WebSymbolDocumentation.create(this)

  @JvmDefault
  override fun getDocumentationTarget(): DocumentationTarget =
    WebSymbolDocumentationTargetImpl(this)

  @JvmDefault
  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
    emptyList()

  @JvmDefault
  override fun getSymbolPresentation(): SymbolPresentation {
    @Suppress("HardCodedStringLiteral")
    val description = if (name.contains(' ')) {
      "${name} ${matchedName}"
    }
    else {
      // TODO use kind description provider
      val kindName = kind.replace('-', ' ').lowercase(Locale.US).let {
        when {
          it.endsWith("ies") -> it.substring(0, it.length - 3) + "y"
          it.endsWith("ses") -> it.substring(0, it.length - 2)
          it.endsWith("s") -> it.substring(0, it.length - 1)
          else -> it
        }
      }
      "${namespace} $kindName '$matchedName'"
    }
    return SymbolPresentation.create(icon, name, description, description)
  }

  @JvmDefault
  val presentation: TargetPresentation
    get() = symbolPresentation.let {
      TargetPresentation.builder(it.shortDescription)
        .icon(it.icon)
        .presentation()
    }

  @JvmDefault
  fun isEquivalentTo(symbol: Symbol): Boolean =
    this == symbol

  @JvmDefault
  fun adjustNameForRefactoring(registry: WebSymbolsRegistry, newName: String, occurence: String): String =
    registry.namesProvider.adjustRename(namespace, kind, matchedName, newName, occurence)

  @JvmDefault
  fun validateName(name: String): String? = null

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

    const val KIND_JS_EVENTS = "events"
    const val KIND_JS_PROPERTIES = "properties"

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