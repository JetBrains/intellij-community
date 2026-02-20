// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector
import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageSearcher
import com.intellij.find.usages.symbol.SearchTargetSymbol
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.navigation.NavigatableSymbol
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.polySymbols.PolySymbol.Companion.PROP_DOC_HIDE_ICON
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.documentation.PolySymbolDocumentationCustomizer
import com.intellij.polySymbols.impl.PolySymbolPropertyGetter
import com.intellij.polySymbols.query.PolySymbolMatch
import com.intellij.polySymbols.query.PolySymbolMatchCustomizer
import com.intellij.polySymbols.query.PolySymbolQueryExecutor
import com.intellij.polySymbols.query.PolySymbolQueryExecutorFactory
import com.intellij.polySymbols.query.PolySymbolQueryScopeContributor
import com.intellij.polySymbols.query.PolySymbolScope
import com.intellij.polySymbols.query.PolySymbolWithPattern
import com.intellij.polySymbols.refactoring.PolySymbolRenameTarget
import com.intellij.polySymbols.search.PolySymbolSearchTarget
import com.intellij.polySymbols.utils.PolySymbolPrioritizedScope
import com.intellij.polySymbols.utils.kindName
import com.intellij.polySymbols.utils.matchedNameOrName
import com.intellij.polySymbols.utils.namespace
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.api.RenameUsageSearcher
import com.intellij.refactoring.rename.symbol.RenameableSymbol
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus
import java.util.Locale
import javax.swing.Icon
import kotlin.reflect.KClass

/**
 * The core element of the Poly Symbols framework. It is identified through `name` and `kind` properties.
 * The symbol has a very generic meaning and may represent a variable in some language, or an endpoint of some web server,
 * or a file.
 *
 * Symbols, which share some common characteristics should be grouped using the same `kind`.
 * The `kind` consists of a `namespace`, which roughly indicates a language or a framework the symbol belongs to,
 * and a `kindName`, which roughly indicates, what the symbol basic characteristics are.
 *
 * [PolySymbol]s provide a straightforward way of implementing:
 * - navigation support - through [PolySymbol.getNavigationTargets] method
 * - documentation support - through [PolySymbol.getDocumentationTarget] method and
 *   [com.intellij.polySymbols.documentation.PolySymbolDocumentationTarget.create] utility
 * - search support - through [PolySymbol.searchTarget] property. [PolySymbol] may also implement the [SearchTargetSymbol] or
 *   [SearchTarget] interfaces, if a non-[PolySymbolSearchTarget] is required. In such a case, however, the search support
 *   will have to be implemented from scratch using [UsageSearcher].
 * - rename support - through [PolySymbol.renameTarget] property. [PolySymbol] may also implement the [RenameableSymbol] or
 *   [RenameTarget] interfaces, if a non-[PolySymbolRenameTarget] is required. In such a case, however, the rename support
 *   will have to be implemented from scratch using [RenameUsageSearcher].
 *
 * Symbols should be provided to the framework through [PolySymbolQueryScopeContributor] and
 * retrieved using [PolySymbolQueryExecutor] acquired using [PolySymbolQueryExecutorFactory].
 * The PolySymbol framework requires an integration into language or framework support through:
 * - completion provider - you can use `com.intellij.polySymbols.completion.PolySymbolsCompletionProviderBase`
 * - reference contributor - use `com.intellij.polySymbols.references.PsiPolySymbolReferenceProvider`
 *
 * The [PolySymbolQueryExecutor] queries support evaluation of [PolySymbolWithPattern] patterns.
 * Symbols, which implement this interface and provide a pattern are expanded to a [PolySymbolMatch]
 * composite symbols during queries. Such patterns allow for handy implementation of a microsyntax.
 *
 * Each symbol can be a scope for other symbols (like a Java class is a scope for its methods and fields). Such symbols
 * should implement [PolySymbolScope] interface. When pattern evaluation is happening, each matched symbol's `queryScope`
 * is put on the scope stack, allowing for expanding the scope during the pattern match process.
 *
 * The symbol lifecycle is a single read action. If you need it to survive between read actions,
 * use [PolySymbol.createPointer] to create a symbol pointer.
 * If the symbol is still valid, dereferencing the pointer might return a new instance of the symbol.
 * During write action, the symbol might not survive PSI tree commit, so you should create a pointer
 * before the commit and dereference it afterward.
 *
 * See also: [Implementing Poly Symbols](https://plugins.jetbrains.com/docs/intellij/websymbols-implementation.html)
 */
/*
 * INAPPLICABLE_JVM_NAME -> https://youtrack.jetbrains.com/issue/KT-31420
 **/
@Suppress("INAPPLICABLE_JVM_NAME")
interface PolySymbol : Symbol, NavigatableSymbol, PolySymbolPrioritizedScope {

  /**
   * Describes which group of symbols (kind) within the particular language
   * or concept (namespace) the symbol belongs to.
   */
  val kind: PolySymbolKind

  /**
   * The name of the symbol. If the symbol does not have a pattern, the name will be used as-is for matching.
   */
  val name: @NlsSafe String

  /**
   * A set of symbol modifiers. The framework contains constants for many modifiers known from various
   * programming languages. However, implementations are free to define other modifiers using [PolySymbolModifier.get].
   *
   * When a match is performed over a sequence of symbols, use [PolySymbolMatchCustomizer] to customize
   * how modifiers from different symbols in the sequence are merged for the resulting [PolySymbolMatch] modifiers.
   */
  val modifiers: Set<PolySymbolModifier>
    get() = emptySet()

  /**
   * An optional icon associated with the symbol, which is going to be used across the IDE.
   * To not show an icon in the documentation, for property [PROP_DOC_HIDE_ICON] return `true`.
   * If no icon is provided, in code completion a default icon for symbol namespace and kind will be used.
   */
  val icon: Icon?
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
   * When a pattern is being evaluated, matched symbols can provide additional scope for further resolution of symbols in the pattern sequence.
   * By default, the `queryScope` property returns the symbol itself if it is a [PolySymbolScope].
   */
  val queryScope: List<PolySymbolScope>
    get() = listOfNotNull(this as? PolySymbolScope)

  /**
   * Specifies whether the symbol is an extension.
   * When matched along with a non-extension symbol, it can provide or override some properties of the symbol,
   * or it can extend its scope contents.
   */
  @get:JvmName("isExtension")
  val extension: Boolean
    get() = false

  /**
   * Symbols with higher priority will have precedence over those with lower priority
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
   * Accessor for various symbol properties. Plugins can use properties to provide additional information on the symbol.
   * All properties supported by IDEs are defined through `PROP_*` constants of [PolySymbol] interface.
   * Check their documentation for further reference. To ensure that results are properly casted, use
   * [PolySymbolProperty.tryCast] method for returned values.
   */
  operator fun <T : Any> get(property: PolySymbolProperty<T>): T? =
    PolySymbolPropertyGetter.get(this, property)

  /**
   * Returns [TargetPresentation] used by [SearchTarget] and [RenameTarget].
   * Default implementations of [PolySymbolRenameTarget] and [PolySymbolSearchTarget] use the presentation property.
   */
  @get:RequiresReadLock
  @get:RequiresBackgroundThread
  val presentation: TargetPresentation
    get() {
      // TODO use kind description provider
      val kindName = kindName.replace('-', ' ').lowercase(Locale.US).let {
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
   * returned by [searchTarget] property is ignored. If returned
   * target is not a [PolySymbolSearchTarget], a dedicated
   * [UsageSearcher] needs to be implemented to handle it.
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
   * returned by [renameTarget] property is ignored. If returned
   * target is not a [PolySymbolRenameTarget], a dedicated
   * [RenameUsageSearcher] needs to be implemented to handle it.
   *
   * @see [RenameableSymbol]
   * @see [RenameTarget]
   */
  val renameTarget: PolySymbolRenameTarget?
    get() = null

  /**
   * Used by the Poly Symbols framework to get a [DocumentationTarget], which handles documentation
   * rendering for the symbol. Additional [location] parameter allows calculating more specific
   * properties for the symbol documentation, like inferred generic parameters.
   *
   * By default, [com.intellij.polySymbols.documentation.PolySymbolDocumentationTarget.create] should
   * be used to build the documentation target for the symbol. It allows for documentation to be further
   * customized by [PolySymbolDocumentationCustomizer]s.
   */
  fun getDocumentationTarget(location: PsiElement?): DocumentationTarget? =
    null

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
    emptyList()

  /**
   * Returns the pointer to the symbol, which can survive between read actions.
   * The dereferenced symbol should be valid, i.e. any PSI based properties should return valid PsiElements.
   */
  override fun createPointer(): Pointer<out PolySymbol>

  /**
   * Return `true` if the symbol should be present in the query results
   * in the particular context.
   */
  fun matchContext(context: PolyContext): Boolean =
    true

  /**
   * Returns `true` if two symbols are the same or equivalent for resolve purposes.
   */
  fun isEquivalentTo(symbol: Symbol): Boolean =
    this == symbol

  /**
   * Poly Symbols can have various naming conventions.
   * This method is used by the framework to determine a new name for a symbol based on its occurrence
   *
   * Note: do not implement - to be removed
   */
  @ApiStatus.Internal
  fun adjustNameForRefactoring(
    queryExecutor: PolySymbolQueryExecutor,
    oldName: PolySymbolQualifiedName,
    newName: String,
    occurence: String
  ): String =
    queryExecutor.namesProvider.adjustRename(oldName, newName, occurence)

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

  @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER,
          AnnotationTarget.ANNOTATION_CLASS)
  @Retention(AnnotationRetention.RUNTIME)
  annotation class Property(val property: KClass<*>)

  companion object {

    /**
     * Supported by `html/elements` and `html/attributes` symbols,
     * allows to inject the specified language into HTML element text or HTML attribute value.
     */
    @JvmField
    val PROP_INJECT_LANGUAGE: PolySymbolProperty<String> = PolySymbolProperty["inject-language"]

    /**
     * If a symbol uses a RegEx pattern, usually it will be displayed in a documentation
     * popup section "pattern". Setting this property to `true` hides that section.
     */
    @JvmField
    val PROP_DOC_HIDE_PATTERN: PolySymbolProperty<Boolean> = PolySymbolProperty["doc-hide-pattern"]

    /**
     * If a symbol has an icon associated, it will be shown in the documentation in the definition section
     * by default. Setting this property to `true` hides the icon.
     */
    @JvmField
    val PROP_DOC_HIDE_ICON: PolySymbolProperty<Boolean> = PolySymbolProperty["doc-hide-icon"]

    /**
     * By default, all symbols show up in code completion.
     * Setting this property to true prevents a symbol from showing up in the code completion.
     */
    @JvmField
    val PROP_HIDE_FROM_COMPLETION: PolySymbolProperty<Boolean> = PolySymbolProperty["hide-from-completion"]

    /**
     * Text attributes key of an IntelliJ ColorScheme.
     **/
    @JvmField
    val PROP_IJ_TEXT_ATTRIBUTES_KEY: PolySymbolProperty<String> = PolySymbolProperty["ij-text-attributes-key"]

    @JvmField
    val PROP_READ_WRITE_ACCESS: PolySymbolProperty<ReadWriteAccessDetector.Access> = PolySymbolProperty["ij-read-write-access"]
  }
}

