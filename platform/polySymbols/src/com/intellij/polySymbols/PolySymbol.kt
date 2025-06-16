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
import com.intellij.polySymbols.query.PolySymbolQueryExecutor
import com.intellij.polySymbols.query.PolySymbolScope
import com.intellij.polySymbols.refactoring.PolySymbolRenameTarget
import com.intellij.polySymbols.search.PolySymbolSearchTarget
import com.intellij.polySymbols.utils.*
import com.intellij.psi.PsiElement
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
interface PolySymbol : Symbol, NavigatableSymbol, PolySymbolPrioritizedScope {

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
   * A set of symbol modifiers
   */
  val modifiers: Set<PolySymbolModifier>
    get() = emptySet()

  /**
   * An optional icon associated with the symbol, which is going to be used across the IDE.
   * If none is specified, a default icon of the origin will be used and if that’s not available,
   * a default icon for symbol namespace and kind.
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
   * When pattern is being evaluated, matched symbols can provide additional scope for further resolution in the pattern.
   * By default, the `queryScope` returns the symbol itself if it is a [PolySymbolScope]
   */
  val queryScope: List<PolySymbolScope>
    get() = listOfNotNull(this as? PolySymbolScope)

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
   * Accessor for various symbol properties. This is a convenience method which
   * tries to cast the value to an expected type for the defined property.
   * Plugins can use properties to provide additional information on the symbol.
   * All properties supported by IDEs are defined through `PROP_*` constants of [PolySymbol] interface.
   * Check their documentation for further reference.
   */
  operator fun <T : Any> get(property: PolySymbolProperty<T>): T? =
    null

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
  fun adjustNameForRefactoring(queryExecutor: PolySymbolQueryExecutor, newName: String, occurence: String): String =
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
  }
}

