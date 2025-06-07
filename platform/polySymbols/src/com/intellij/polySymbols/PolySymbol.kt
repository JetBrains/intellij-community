// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols

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
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.html.PolySymbolHtmlAttributeValue
import com.intellij.polySymbols.js.PolySymbolJsKind
import com.intellij.polySymbols.patterns.PolySymbolsPattern
import com.intellij.polySymbols.query.PolySymbolMatch
import com.intellij.polySymbols.query.PolySymbolsQueryExecutor
import com.intellij.polySymbols.refactoring.PolySymbolRenameTarget
import com.intellij.polySymbols.search.PolySymbolSearchTarget
import com.intellij.polySymbols.utils.*
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.symbol.RenameableSymbol
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.util.*
import javax.swing.Icon

/**
 * The core element of the Poly Symbols framework - it represents an entity in the Poly Symbols model.
 * It is identified through `namespace`, `kind` and `name` properties.
 *
 * The symbol lifecycle is a single read action. If you need it to survive between read actions, use [PolySymbol.createPointer] to create a symbol pointer.
 * If the symbol is still valid, dereferencing the pointer will return a new instance of the symbol.
 * During write action, the symbol might not survive PSI tree commit, so you should create a pointer
 * before the commit and dereference it afterward.
 *
 * See also: [Implementing Poly Symbols](https://plugins.jetbrains.com/docs/intellij/websymbols-implementation.html)
 *
 */
/*
 * INAPPLICABLE_JVM_NAME -> https://youtrack.jetbrains.com/issue/KT-31420
 **/
@Suppress("INAPPLICABLE_JVM_NAME")
interface PolySymbol : PolySymbolsScope, Symbol, NavigatableSymbol, PolySymbolsPrioritizedScope {

  /**
   * Specifies where this symbol comes from. Besides descriptive information like
   * framework, library, version or default icon, it also provides an interface to
   * load symbol types and icons.
   **/
  val origin: PolySymbolOrigin

  /**
   * Describes which group of symbols (kind) within the particular language
   * or concept (namespace) the symbol belongs to.
   */
  val qualifiedKind: PolySymbolQualifiedKind

  /**
   * The name of the symbol. If the symbol does not have a pattern, the name will be used as-is for matching.
   */
  val name: @NlsSafe String

  /**
   * An optional icon associated with the symbol, which is going to be used across the IDE.
   * If none is specified, a default icon of the origin will be used and if that’s not available,
   * a default icon for symbol namespace and kind.
   */
  val icon: Icon?
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
   * Documents API status of the symbol. It is one of the sub-interfaces of [PolySymbolApiStatus]:
   * [PolySymbolApiStatus.Stable], [PolySymbolApiStatus.Experimental], [PolySymbolApiStatus.Deprecated]
   * or [PolySymbolApiStatus.Obsolete].
   *
   * Deprecated and obsolete symbols are appropriately highlighted in the code editor, code completion and
   * quick documentation.
   */
  val apiStatus: PolySymbolApiStatus
    get() = PolySymbolApiStatus.Stable

  /**
   * A special property to support symbols representing HTML attributes.
   */
  val attributeValue: PolySymbolHtmlAttributeValue?
    get() = null

  /**
   * The pattern to match names against. As a result of pattern matching a [PolySymbolMatch] will be created.
   * A pattern may specify that a reference to other Poly Symbols is expected in some part of it.
   * For such places, appropriate segments with referenced Poly Symbols will be created and navigation,
   * validation and refactoring support is available out-of-the-box.
   */
  val pattern: PolySymbolsPattern?
    get() = null

  /**
   * When pattern is being evaluated, matched symbols can provide additional scope for further resolution in the pattern.
   * By default, the `queryScope` returns the symbol itself
   */
  val queryScope: List<PolySymbolsScope>
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
   * A [PsiElement], which is a file or an element, which can be used to roughly
   * locate the source of the symbol within a project to provide a context for loading additional information,
   * like types. If the symbol is [com.intellij.polySymbols.search.PsiSourcedPolySymbol], then `psiContext` is equal to source.
   */
  val psiContext: PsiElement?
    get() = null

  /**
   * Various symbol properties. There should be no assumption on the type of properties.
   * Properties can be used by plugins to provide additional information on the symbol.
   * All properties supported by IDEs are defined through `PROP_*` constants  of [PolySymbol] interface.
   * Check properties documentation for further reference.
   */
  val properties: Map<String, Any>
    get() = emptyMap()

  /**
   * Returns [TargetPresentation] used by [SearchTarget] and [RenameTarget].
   * Default implementations of [PolySymbolRenameTarget] and [PolySymbolSearchTarget] use the presentation property.
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
   * [PolySymbolSearchTarget.create].
   *
   * Symbol can also implement [SearchTarget] interface directly
   * and override its methods, in which case [PolySymbolSearchTarget]
   * returned by [searchTarget] property is ignored.
   *
   * @see [SearchTargetSymbol]
   * @see [SearchTarget]
   */
  val searchTarget: PolySymbolSearchTarget?
    get() = null

  /**
   * Implement to provide rename refactoring for the symbol.
   * In most cases the implementation would simply create
   * [PolySymbolRenameTarget] object.
   *
   * Symbol can also implement [RenameTarget] interface directly
   * and override its methods, in which case [PolySymbolRenameTarget]
   * returned by [renameTarget] property is ignored.
   *
   * @see [RenameableSymbol]
   * @see [RenameTarget]
   */
  val renameTarget: PolySymbolRenameTarget?
    get() = null

  /**
   * Used by Poly Symbols framework to get a [DocumentationTarget], which handles documentation
   * rendering for the symbol. You may implement [com.intellij.polySymbols.documentation.PolySymbolWithDocumentation]
   * interface, which will provide a default implementation to render the documentation.
   */
  fun getDocumentationTarget(location: PsiElement?): DocumentationTarget? =
    null

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
    emptyList()

  /**
   * Symbols can be used in [CachedValue]s as dependencies.
   * If a symbol instance can mutate over time, it should properly implement this method.
   */
  override fun getModificationCount(): Long = 0

  /**
   * Returns the pointer to the symbol, which can survive between read actions.
   * The dereferenced symbol should be valid, i.e. any PSI based properties should return valid PsiElements.
   */
  override fun createPointer(): Pointer<out PolySymbol>

  /**
   * Return `true` if the symbol should be present in the query results
   * in the particular context. By default, the current symbol framework is checked.
   */
  fun matchContext(context: PolyContext): Boolean =
    origin.framework == null || context.framework == null || origin.framework == context.framework

  /**
   * Returns `true` if two symbols are the same or equivalent for resolve purposes.
   */
  fun isEquivalentTo(symbol: Symbol): Boolean =
    this == symbol

  /**
   * Poly Symbols can have various naming conventions.
   * This method is used by the framework to determine a new name for a symbol based on its occurrence
   */
  fun adjustNameForRefactoring(queryExecutor: PolySymbolsQueryExecutor, newName: String, occurence: String): String =
    queryExecutor.namesProvider.adjustRename(qualifiedName, newName, occurence)


  sealed interface Priority : Comparable<Priority> {

    val value: Double

    companion object {
      @JvmField
      val LOWEST: Priority = PolySymbolPriority(0.0, "LOWEST")

      @JvmField
      val LOW: Priority = PolySymbolPriority(1.0, "LOW")

      @JvmField
      val NORMAL: Priority = PolySymbolPriority(10.0, "NORMAL")

      @JvmField
      val HIGH: Priority = PolySymbolPriority(50.0, "HIGH")

      @JvmField
      val HIGHEST: Priority = PolySymbolPriority(100.0, "HIGHEST")

      @JvmStatic
      fun custom(value: Double): Priority = PolySymbolPriority(value, "CUSTOM($value)")

      private class PolySymbolPriority(override val value: Double, private val displayString: String) : Priority {
        override fun compareTo(other: Priority): Int =
          value.compareTo(other.value)

        override fun equals(other: Any?): Boolean =
          other === this || other is PolySymbolPriority && other.value == value

        override fun hashCode(): Int =
          value.hashCode()

        override fun toString(): String = displayString
      }
    }
  }

  companion object {
    const val NAMESPACE_HTML: String = "html"
    const val NAMESPACE_CSS: String = "css"
    const val NAMESPACE_JS: String = "js"

    val HTML_ELEMENTS: PolySymbolQualifiedKind = PolySymbolQualifiedKind(NAMESPACE_HTML, "elements")
    val HTML_ATTRIBUTES: PolySymbolQualifiedKind = PolySymbolQualifiedKind(NAMESPACE_HTML, "attributes")
    val HTML_ATTRIBUTE_VALUES: PolySymbolQualifiedKind = PolySymbolQualifiedKind(NAMESPACE_HTML, "values")
    val HTML_SLOTS: PolySymbolQualifiedKind = PolySymbolQualifiedKind(NAMESPACE_HTML, "slots")

    val CSS_PROPERTIES: PolySymbolQualifiedKind = PolySymbolQualifiedKind(NAMESPACE_CSS, "properties")
    val CSS_PSEUDO_ELEMENTS: PolySymbolQualifiedKind = PolySymbolQualifiedKind(NAMESPACE_CSS, "pseudo-elements")
    val CSS_PSEUDO_CLASSES: PolySymbolQualifiedKind = PolySymbolQualifiedKind(NAMESPACE_CSS, "pseudo-classes")
    val CSS_FUNCTIONS: PolySymbolQualifiedKind = PolySymbolQualifiedKind(NAMESPACE_CSS, "functions")
    val CSS_CLASSES: PolySymbolQualifiedKind = PolySymbolQualifiedKind(NAMESPACE_CSS, "classes")
    val CSS_PARTS: PolySymbolQualifiedKind = PolySymbolQualifiedKind(NAMESPACE_CSS, "parts")

    val JS_EVENTS: PolySymbolQualifiedKind = PolySymbolQualifiedKind(NAMESPACE_JS, "events")
    val JS_PROPERTIES: PolySymbolQualifiedKind = PolySymbolQualifiedKind(NAMESPACE_JS, "properties")
    val JS_KEYWORDS: PolySymbolQualifiedKind = PolySymbolQualifiedKind(NAMESPACE_JS, "keywords")
    val JS_SYMBOLS: PolySymbolQualifiedKind = PolySymbolQualifiedKind(NAMESPACE_JS, "symbols")
    val JS_STRING_LITERALS: PolySymbolQualifiedKind = PolySymbolQualifiedKind(NAMESPACE_JS, "string-literals")

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
     * Name of [PolySymbolJsKind] property used by `js/symbols` symbols to specify kind of the JS symbol.
     * By default, JS symbol is treated as [PolySymbolJsKind.Variable].
     **/
    const val PROP_KIND: String = "kind"

    /**
     * Name of [PolySymbolJsKind] property used by other symbols to specify kind of the JS symbol.
     * By default, JS symbol is treated as [PolySymbolJsKind.Variable].
     **/
    const val PROP_JS_SYMBOL_KIND: String = "js-symbol-kind"

    /**
     * Don't provide documentation for the symbol
     */
    const val PROP_NO_DOC: String = "ij-no-doc"

    /**
     * Text attributes key of an IntelliJ ColorScheme.
     **/
    const val PROP_IJ_TEXT_ATTRIBUTES_KEY: String = "ij-text-attributes-key"
  }
}