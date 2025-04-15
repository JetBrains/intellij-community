// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.symbol.SearchTargetSymbol
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.navigation.NavigatableSymbol
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.symbol.RenameableSymbol
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.webSymbols.context.WebSymbolsContext
import com.intellij.webSymbols.documentation.WebSymbolDocumentation
import com.intellij.webSymbols.documentation.WebSymbolDocumentationCustomizer
import com.intellij.webSymbols.documentation.impl.WebSymbolDocumentationTargetImpl
import com.intellij.webSymbols.html.WebSymbolHtmlAttributeValue
import com.intellij.webSymbols.js.WebSymbolJsKind
import com.intellij.webSymbols.patterns.WebSymbolsPattern
import com.intellij.webSymbols.query.WebSymbolMatch
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor
import com.intellij.webSymbols.refactoring.WebSymbolRenameTarget
import com.intellij.webSymbols.search.WebSymbolSearchTarget
import com.intellij.webSymbols.utils.matchedNameOrName
import com.intellij.webSymbols.utils.qualifiedName
import org.jetbrains.annotations.Nls
import java.util.*
import javax.swing.Icon

/**
 * The core element of the Web Symbols framework - it represents an entity in the Web Symbols model.
 * It is identified through `namespace`, `kind` and `name` properties.
 *
 * The symbol lifecycle is a single read action. If you need it to survive between read actions, use [WebSymbol.createPointer] to create a symbol pointer.
 * If the symbol is still valid, dereferencing the pointer will return a new instance of the symbol.
 * During write action, the symbol might not survive PSI tree commit, so you should create a pointer
 * before the commit and dereference it afterward.
 *
 * See also: [Implementing Web Symbols](https://plugins.jetbrains.com/docs/intellij/websymbols-implementation.html)
 *
 */
/*
 * INAPPLICABLE_JVM_NAME -> https://youtrack.jetbrains.com/issue/KT-31420
 **/
@Suppress("INAPPLICABLE_JVM_NAME")
interface WebSymbol : WebSymbolsScope, Symbol, NavigatableSymbol, WebSymbolsPrioritizedScope {

  /**
   * Specifies where this symbol comes from. Besides descriptive information like
   * framework, library, version or default icon, it also provides an interface to
   * load symbol types and icons.
   **/
  val origin: WebSymbolOrigin

  /**
   * Describes which language or concept the symbol belongs to.
   */
  val namespace: @NlsSafe SymbolNamespace

  /**
   * Describes which group of symbols within the particular language
   * or concept (namespace) the symbol belongs to.
   * The kind should be plural in form, e.g. "attributes".
   */
  val kind: @NlsSafe SymbolKind

  /**
   * The name of the symbol. If the symbol does not have a pattern, the name will be used as-is for matching.
   */
  val name: @NlsSafe String

  /**
   * An optional text, which describes the symbol purpose and usage.
   * It is rendered in the documentation popup or view.
   */
  val description: @Nls String?
    get() = null

  /**
   * Additional sections, to be rendered in the symbols’ documentation.
   * Each section should have a name, but the contents can be empty.
   */
  val descriptionSections: Map<@Nls String, @Nls String>
    get() = emptyMap()

  /**
   * An optional URL to a website with detailed symbol's documentation
   */
  val docUrl: @NlsSafe String?
    get() = null

  /**
   * An optional icon associated with the symbol, which is going to be used across the IDE.
   * If none is specified, a default icon of the origin will be used and if that’s not available,
   * a default icon for symbol namespace and kind.
   */
  val icon: Icon?
    get() = null

  /**
   * If the symbol represents some property, variable or anything that can hold a value,
   * this property documents what is the default value.
   */
  val defaultValue: @NlsSafe String?
    get() = null

  /**
   * The type of the symbol. The type can be interpreted only within the context of symbol origin
   * and in regard to its namespace and kind. The type may be a language type,
   * coming from e.g. Java or TypeScript, or it may be any arbitrary value.
   * Usually a type would be associated with symbols, which can hold a value,
   * or represent some language symbol, like class, method, etc.
   */
  val type: Any?
    get() = null

  /**
   * Whether this symbol is required. What "is required" means, depends on the symbol.
   * For instance, for an HTML attribute it would mean that the attribute is required to be present
   * for the particular HTML element. For JavaScript property is would mean that it is not optional,
   * so it cannot be undefined.
   */
  @get:JvmName("isRequired")
  val required: Boolean?
    get() = null

  /**
   * Documents API status of the symbol. It is one of the sub-interfaces of [WebSymbolApiStatus]:
   * [WebSymbolApiStatus.Stable], [WebSymbolApiStatus.Experimental], [WebSymbolApiStatus.Deprecated]
   * or [WebSymbolApiStatus.Obsolete].
   *
   * Deprecated and obsolete symbols are appropriately highlighted in the code editor, code completion and
   * quick documentation.
   */
  val apiStatus: WebSymbolApiStatus
    get() = WebSymbolApiStatus.Stable

  /**
   * A special property to support symbols representing HTML attributes.
   */
  val attributeValue: WebSymbolHtmlAttributeValue?
    get() = null

  /**
   * The pattern to match names against. As a result of pattern matching a [WebSymbolMatch] will be created.
   * A pattern may specify that a reference to other Web Symbols is expected in some part of it.
   * For such places, appropriate segments with referenced Web Symbols will be created and navigation,
   * validation and refactoring support is available out-of-the-box.
   */
  val pattern: WebSymbolsPattern?
    get() = null

  /**
   * When pattern is being evaluated, matched symbols can provide additional scope for further resolution in the pattern.
   * By default, the `queryScope` returns the symbol itself
   */
  val queryScope: List<WebSymbolsScope>
    get() = listOf(this)

  /**
   * Some symbols represent only a framework syntax,
   * which does not translate to a particular symbol in the runtime.
   * For instance a Vue directive, which needs to be prefixed with `v-` will result in
   * some special code generated, but as such is not a real HTML attribute.
   * This distinction allows us to ignore such symbols when looking for references.
   */
  @get:JvmName("isVirtual")
  val virtual: Boolean
    get() = false

  /**
   * Some symbols may have a lot in common with each other and
   * one can use abstract symbols as their super symbol.
   * For performance reasons, only statically defined symbols (Web Types, Custom Element Manifest)
   * can inherit from other statically defined symbols.
   * For dynamically defined symbols you should use regular class inheritance.
   */
  @get:JvmName("isAbstract")
  val abstract: Boolean
    get() = false

  /**
   * Specifies whether the symbol is an extension.
   * When matched along with a non-extension symbol it can provide or override some properties of the symbol,
   * or it can extend its scope contents.
   */
  @get:JvmName("isExtension")
  val extension: Boolean
    get() = false

  /**
   * Symbols with higher priority will have precedence over those with lower priority,
   * when matching is performed. Symbols with higher priority will also show higher on the completion list.
   */
  override val priority: Priority?
    get() = null

  /**
   * Provides additional way to sort symbols in code completion list within a particular priority.
   * The value must be a non-negative integer and the higher proximity,
   * the higher the symbol would be on the list.
   */
  val proximity: Int?
    get() = null

  /**
   * A [PsiElement], which is a file or an element, which can be used to roughly
   * locate the source of the symbol within a project to provide a context for loading additional information,
   * like types. If the symbol is [PsiSourcedWebSymbol], then `psiContext` is equal to source.
   */
  val psiContext: PsiElement?
    get() = null

  /**
   * Various symbol properties. There should be no assumption on the type of properties.
   * Properties can be used by plugins to provide additional information on the symbol.
   * All properties supported by IDEs are defined through `PROP_*` constants  of [WebSymbol] interface.
   * Check properties documentation for further reference.
   */
  val properties: Map<String, Any>
    get() = emptyMap()

  /**
   * Returns [TargetPresentation] used by [SearchTarget] and [RenameTarget].
   * Default implementations of [WebSymbolRenameTarget] and [WebSymbolSearchTarget] use the presentation property.
   */
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

  /**
   * Implement to provide usage search for the symbol.
   * In most cases the implementation would simply call
   * [WebSymbolSearchTarget.create].
   *
   * Symbol can also implement [SearchTarget] interface directly
   * and override its methods, in which case [WebSymbolSearchTarget]
   * returned by [searchTarget] property is ignored.
   *
   * @see [SearchTargetSymbol]
   * @see [SearchTarget]
   */
  val searchTarget: WebSymbolSearchTarget?
    get() = null

  /**
   * Implement to provide rename refactoring for the symbol.
   * In most cases the implementation would simply create
   * [WebSymbolRenameTarget] object.
   *
   * Symbol can also implement [RenameTarget] interface directly
   * and override its methods, in which case [WebSymbolRenameTarget]
   * returned by [renameTarget] property is ignored.
   *
   * @see [RenameableSymbol]
   * @see [RenameTarget]
   */
  val renameTarget: WebSymbolRenameTarget?
    get() = null

  /**
   * Used by Web Symbols framework to get a [DocumentationTarget], which handles documentation
   * rendering for the symbol. Default implementation will use [createDocumentation]
   * to render the documentation.
   */
  fun getDocumentationTarget(location: PsiElement?): DocumentationTarget =
    WebSymbolDocumentationTargetImpl(this, location)

  /**
   * Returns [WebSymbolDocumentation] - an interface holding information required to render documentation for the symbol.
   * By default, it's contents are build from the available Web Symbol information.
   *
   * To customize symbols documentation, one can override the method, or implement [WebSymbolDocumentationCustomizer].
   *
   * [WebSymbolDocumentation] interface provides builder methods for customizing the documentation.
   * `with*` methods return a copy of the documentation with customized fields.
   */
  fun createDocumentation(location: PsiElement?): WebSymbolDocumentation? =
    WebSymbolDocumentation.create(this, location)

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
    emptyList()

  /**
   * Symbols can be used in [CachedValue]s as dependencies.
   * If a symbol instance can mutate over the time, it should properly implement this method.
   */
  override fun getModificationCount(): Long = 0

  /**
   * Returns the pointer to the symbol, which can survive between read actions.
   * The dereferenced symbol should be valid, i.e. any PSI based properties should return valid PsiElements.
   */
  override fun createPointer(): Pointer<out WebSymbol>

  /**
   * Return `true` if the symbol should be present in the query results
   * in the particular context. By default, the current symbol framework is checked.
   */
  fun matchContext(context: WebSymbolsContext): Boolean =
    origin.framework == null || context.framework == null || origin.framework == context.framework

  /**
   * Returns `true` if two symbols are the same or equivalent for resolve purposes.
   */
  fun isEquivalentTo(symbol: Symbol): Boolean =
    this == symbol

  /**
   * Web Symbols can have various naming conventions.
   * This method is used by the framework to determine a new name for a symbol based on its occurrence
   */
  fun adjustNameForRefactoring(queryExecutor: WebSymbolsQueryExecutor, newName: String, occurence: String): String =
    queryExecutor.namesProvider.adjustRename(qualifiedName, newName, occurence)


  enum class Priority(val value: Double) {
    LOWEST(0.0),
    LOW(1.0),
    NORMAL(10.0),
    HIGH(50.0),
    HIGHEST(100.0);
  }

  companion object {
    const val NAMESPACE_HTML: String = "html"
    const val NAMESPACE_CSS: String = "css"
    const val NAMESPACE_JS: String = "js"

    const val KIND_HTML_ELEMENTS: String = "elements"
    const val KIND_HTML_ATTRIBUTES: String = "attributes"
    const val KIND_HTML_ATTRIBUTE_VALUES: String = "values"
    const val KIND_HTML_SLOTS: String = "slots"

    const val KIND_CSS_PROPERTIES: String = "properties"
    const val KIND_CSS_PSEUDO_ELEMENTS: String = "pseudo-elements"
    const val KIND_CSS_PSEUDO_CLASSES: String = "pseudo-classes"
    const val KIND_CSS_FUNCTIONS: String = "functions"
    const val KIND_CSS_CLASSES: String = "classes"
    const val KIND_CSS_PARTS: String = "parts"

    const val KIND_JS_EVENTS: String = "events"
    const val KIND_JS_PROPERTIES: String = "properties"
    const val KIND_JS_SYMBOLS: String = "symbols"
    const val KIND_JS_STRING_LITERALS: String = "string-literals"

    val HTML_ELEMENTS: WebSymbolQualifiedKind = WebSymbolQualifiedKind(NAMESPACE_HTML, KIND_HTML_ELEMENTS)
    val HTML_ATTRIBUTES: WebSymbolQualifiedKind = WebSymbolQualifiedKind(NAMESPACE_HTML, KIND_HTML_ATTRIBUTES)
    val HTML_ATTRIBUTE_VALUES: WebSymbolQualifiedKind = WebSymbolQualifiedKind(NAMESPACE_HTML, KIND_HTML_ATTRIBUTE_VALUES)
    val HTML_SLOTS: WebSymbolQualifiedKind = WebSymbolQualifiedKind(NAMESPACE_HTML, KIND_HTML_SLOTS)

    val CSS_PROPERTIES: WebSymbolQualifiedKind = WebSymbolQualifiedKind(NAMESPACE_CSS, KIND_CSS_PROPERTIES)
    val CSS_PSEUDO_ELEMENTS: WebSymbolQualifiedKind = WebSymbolQualifiedKind(NAMESPACE_CSS, KIND_CSS_PSEUDO_ELEMENTS)
    val CSS_PSEUDO_CLASSES: WebSymbolQualifiedKind = WebSymbolQualifiedKind(NAMESPACE_CSS, KIND_CSS_PSEUDO_CLASSES)
    val CSS_FUNCTIONS: WebSymbolQualifiedKind = WebSymbolQualifiedKind(NAMESPACE_CSS, KIND_CSS_FUNCTIONS)
    val CSS_CLASSES: WebSymbolQualifiedKind = WebSymbolQualifiedKind(NAMESPACE_CSS, KIND_CSS_CLASSES)
    val CSS_PARTS: WebSymbolQualifiedKind = WebSymbolQualifiedKind(NAMESPACE_CSS, KIND_CSS_PARTS)

    val JS_EVENTS: WebSymbolQualifiedKind = WebSymbolQualifiedKind(NAMESPACE_JS, KIND_JS_EVENTS)
    val JS_PROPERTIES: WebSymbolQualifiedKind = WebSymbolQualifiedKind(NAMESPACE_JS, KIND_JS_PROPERTIES)
    val JS_SYMBOLS: WebSymbolQualifiedKind = WebSymbolQualifiedKind(NAMESPACE_JS, KIND_JS_SYMBOLS)
    val JS_STRING_LITERALS: WebSymbolQualifiedKind = WebSymbolQualifiedKind(NAMESPACE_JS, KIND_JS_STRING_LITERALS)

    /**
     * Supported by `html/elements` and `html/attributes` symbols,
     * allows to inject the specified language into HTML element text or HTML attribute value.
     */
    const val PROP_INJECT_LANGUAGE: String = "inject-language"

    /**
     * If a symbol uses a RegEx pattern, usually it will be displayed in a documentation
     * popup section "pattern". Setting this property to `true` hides that section.
     */
    const val PROP_DOC_HIDE_PATTERN: String = "doc-hide-pattern"

    /**
     * By default, all symbols show up in code completion.
     * Setting this property to true prevents a symbol from showing up in the code completion.
     */
    const val PROP_HIDE_FROM_COMPLETION: String = "hide-from-completion"

    /**
     * Name of boolean property used by `css/pseudo-elements` and `css/pseudo-classes` symbols
     * to specify whether they require arguments. Defaults to false.
     **/
    const val PROP_ARGUMENTS: String = "arguments"

    /**
     * Name of boolean property used by `js/properties` symbols to specify whether
     * the property is read-only. Defaults to false.
     **/
    const val PROP_READ_ONLY: String = "read-only"

    /**
     * Name of [WebSymbolJsKind] property used by `js/symbols` symbols to specify kind of the JS symbol.
     * By default, JS symbol is treated as [WebSymbolJsKind.Variable].
     **/
    const val PROP_KIND: String = "kind"

    /**
     * Name of [WebSymbolJsKind] property used by other symbols to specify kind of the JS symbol.
     * By default, JS symbol is treated as [WebSymbolJsKind.Variable].
     **/
    const val PROP_JS_SYMBOL_KIND: String = "js-symbol-kind"

    /**
     * Text attributes key of an IntelliJ ColorScheme.
     **/
    const val PROP_IJ_TEXT_ATTRIBUTES_KEY: String = "ij-text-attributes-key"
  }
}