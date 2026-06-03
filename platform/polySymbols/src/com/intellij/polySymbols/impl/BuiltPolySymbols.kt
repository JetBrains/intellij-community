// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.impl

import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.PolySymbolProperty
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.documentation.PolySymbolDocumentationBuilder
import com.intellij.polySymbols.documentation.PolySymbolDocumentationTarget
import com.intellij.polySymbols.BuiltPolySymbol
import com.intellij.polySymbols.DependencyHandle
import com.intellij.polySymbols.PolySymbolDeclarationSite
import com.intellij.polySymbols.impl.DependencyScope.Companion.dependencyScope
import com.intellij.polySymbols.patterns.PolySymbolPattern
import com.intellij.polySymbols.patterns.PolySymbolPatternBuilder
import com.intellij.polySymbols.patterns.polySymbolPattern
import com.intellij.polySymbols.query.PolySymbolWithPattern
import com.intellij.polySymbols.refactoring.PolySymbolRenameTarget
import com.intellij.polySymbols.search.PolySymbolSearchTarget
import com.intellij.polySymbols.search.PsiLinkedPolySymbol
import com.intellij.polySymbols.utils.PolySymbolDeclaredInPsi
import com.intellij.psi.PsiElement
import javax.swing.Icon

/**
 * Snapshot of the mode-independent state captured at `build()` time.
 * Mode-specific fields (pattern builder, source getter, declaration site)
 * are passed directly to the matching specialized `BuiltPolySymbol*` class.
 */
internal sealed interface BuiltConfig {
  val kind: PolySymbolKind
  val name: String

  val priorityValue: PolySymbol.Priority? get() = null
  val priorityGetter: (() -> PolySymbol.Priority?)? get() = null
  val apiStatusValue: PolySymbolApiStatus? get() = null
  val apiStatusGetter: (() -> PolySymbolApiStatus)? get() = null
  val modifiersValue: Set<PolySymbolModifier>? get() = null
  val modifiersGetter: (() -> Set<PolySymbolModifier>)? get() = null
  val iconValue: Icon? get() = null
  val iconGetter: (() -> Icon?)? get() = null
  val extensionValue: Boolean get() = false
  val extensionGetter: (() -> Boolean)? get() = null
  val presentationValue: TargetPresentation? get() = null
  val presentationGetter: (() -> TargetPresentation)? get() = null
  val isSearchTarget: Boolean get() = false
  val isRenameTarget: Boolean get() = false
  val documentationBuilder: (PolySymbolDocumentationBuilder.(BuiltPolySymbol, PsiElement?) -> Unit)? get() = null
  val navigationTargetsGetter: ((Project) -> Collection<NavigationTarget>)? get() = null
  val matchContextGetter: ((PolyContext) -> Boolean)? get() = null
  val isEquivalentToGetter: ((Symbol) -> Boolean)? get() = null
  val propertyValues: Map<PolySymbolProperty<*>, Any?> get() = emptyMap()
  val propertyGetters: Map<PolySymbolProperty<*>, () -> Any?> get() = emptyMap()
}

internal const val FLAG_SEARCH_TARGET: Int = 1 shl 0
internal const val FLAG_RENAME_TARGET: Int = 1 shl 1
internal const val FLAG_EXTENSION: Int = 1 shl 2

internal fun packFlags(isSearchTarget: Boolean, isRenameTarget: Boolean, extension: Boolean): Int {
  var f = 0
  if (isSearchTarget) f = f or FLAG_SEARCH_TARGET
  if (isRenameTarget) f = f or FLAG_RENAME_TARGET
  if (extension) f = f or FLAG_EXTENSION
  return f
}

/** Smallest variant: only `kind` and `name`, everything else uses interface defaults. */
internal data class BuiltConfigMinimal(
  override val kind: PolySymbolKind,
  override val name: String,
) : BuiltConfig

/**
 * Variant for the common "simple symbol" shape:
 * may set priority (value only), property values/getters,
 * and any of the three boolean flags (packed into [flags]).
 * All cold/rare fields use interface defaults.
 */
internal data class BuiltConfigSimple(
  override val kind: PolySymbolKind,
  override val name: String,
  override val priorityValue: PolySymbol.Priority?,
  override val propertyValues: Map<PolySymbolProperty<*>, Any?>,
  override val propertyGetters: Map<PolySymbolProperty<*>, () -> Any?>,
  private val flags: Int,
) : BuiltConfig {
  override val isSearchTarget: Boolean get() = (flags and FLAG_SEARCH_TARGET) != 0
  override val isRenameTarget: Boolean get() = (flags and FLAG_RENAME_TARGET) != 0
  override val extensionValue: Boolean get() = (flags and FLAG_EXTENSION) != 0
}

/** Full-fidelity variant: all 23 logical fields, booleans packed into [flags]. */
internal data class BuiltConfigFull(
  override val kind: PolySymbolKind,
  override val name: String,
  override val priorityValue: PolySymbol.Priority?,
  override val priorityGetter: (() -> PolySymbol.Priority?)?,
  override val apiStatusValue: PolySymbolApiStatus?,
  override val apiStatusGetter: (() -> PolySymbolApiStatus)?,
  override val modifiersValue: Set<PolySymbolModifier>?,
  override val modifiersGetter: (() -> Set<PolySymbolModifier>)?,
  override val iconValue: Icon?,
  override val iconGetter: (() -> Icon?)?,
  override val extensionGetter: (() -> Boolean)?,
  override val presentationValue: TargetPresentation?,
  override val presentationGetter: (() -> TargetPresentation)?,
  override val documentationBuilder: (PolySymbolDocumentationBuilder.(BuiltPolySymbol, PsiElement?) -> Unit)?,
  override val navigationTargetsGetter: ((Project) -> Collection<NavigationTarget>)?,
  override val matchContextGetter: ((PolyContext) -> Boolean)?,
  override val isEquivalentToGetter: ((Symbol) -> Boolean)?,
  override val propertyValues: Map<PolySymbolProperty<*>, Any?>,
  override val propertyGetters: Map<PolySymbolProperty<*>, () -> Any?>,
  private val flags: Int,
) : BuiltConfig {
  override val isSearchTarget: Boolean get() = (flags and FLAG_SEARCH_TARGET) != 0
  override val isRenameTarget: Boolean get() = (flags and FLAG_RENAME_TARGET) != 0
  override val extensionValue: Boolean get() = (flags and FLAG_EXTENSION) != 0
}

private fun BuiltConfig.contentEquals(other: BuiltConfig): Boolean =
  kind == other.kind
  && name == other.name
  && priorityValue == other.priorityValue
  && priorityGetter == other.priorityGetter
  && apiStatusValue == other.apiStatusValue
  && apiStatusGetter == other.apiStatusGetter
  && modifiersValue == other.modifiersValue
  && modifiersGetter == other.modifiersGetter
  && iconValue == other.iconValue
  && iconGetter == other.iconGetter
  && extensionValue == other.extensionValue
  && extensionGetter == other.extensionGetter
  && presentationValue == other.presentationValue
  && presentationGetter == other.presentationGetter
  && isSearchTarget == other.isSearchTarget
  && isRenameTarget == other.isRenameTarget
  && documentationBuilder == other.documentationBuilder
  && navigationTargetsGetter == other.navigationTargetsGetter
  && matchContextGetter == other.matchContextGetter
  && isEquivalentToGetter == other.isEquivalentToGetter
  && propertyValues == other.propertyValues
  && propertyGetters == other.propertyGetters

private fun BuiltConfig.contentHashCode(): Int {
  var result = kind.hashCode()
  result = 31 * result + name.hashCode()
  result = 31 * result + priorityValue.hashCode()
  result = 31 * result + priorityGetter.hashCode()
  result = 31 * result + apiStatusValue.hashCode()
  result = 31 * result + apiStatusGetter.hashCode()
  result = 31 * result + modifiersValue.hashCode()
  result = 31 * result + modifiersGetter.hashCode()
  result = 31 * result + iconValue.hashCode()
  result = 31 * result + iconGetter.hashCode()
  result = 31 * result + extensionValue.hashCode()
  result = 31 * result + extensionGetter.hashCode()
  result = 31 * result + presentationValue.hashCode()
  result = 31 * result + presentationGetter.hashCode()
  result = 31 * result + isSearchTarget.hashCode()
  result = 31 * result + isRenameTarget.hashCode()
  result = 31 * result + documentationBuilder.hashCode()
  result = 31 * result + navigationTargetsGetter.hashCode()
  result = 31 * result + matchContextGetter.hashCode()
  result = 31 * result + isEquivalentToGetter.hashCode()
  result = 31 * result + propertyValues.hashCode()
  result = 31 * result + propertyGetters.hashCode()
  return result
}

internal abstract class BuiltPolySymbolBase(
  protected val config: BuiltConfig,
  private val dependencySource: DependencySource,
  protected val dependencyScope: DependencyScope,
) : BuiltPolySymbol {

  protected abstract fun buildConstructor(): (config: BuiltConfig, source: DependencySource, scope: DependencyScope) -> PolySymbol

  override val kind: PolySymbolKind get() = config.kind
  override val name: String get() = config.name

  override val priority: PolySymbol.Priority?
    get() = config.priorityGetter?.let { dependencyScope.withinScope { it() } }
            ?: config.priorityValue
            ?: super.priority

  override val apiStatus: PolySymbolApiStatus
    get() = config.apiStatusGetter?.let { dependencyScope.withinScope { it() } }
            ?: config.apiStatusValue
            ?: super.apiStatus

  override val modifiers: Set<PolySymbolModifier>
    get() = config.modifiersGetter?.let { dependencyScope.withinScope { it() } }
            ?: config.modifiersValue
            ?: super.modifiers

  override val icon: Icon?
    get() = config.iconGetter?.let { dependencyScope.withinScope { it() } }
            ?: config.iconValue
            ?: super.icon

  override val extension: Boolean
    get() = config.extensionGetter?.let { dependencyScope.withinScope { it() } }
            ?: config.extensionValue

  override val presentation: TargetPresentation
    get() = config.presentationGetter?.let { dependencyScope.withinScope { it() } }
            ?: config.presentationValue
            ?: super.presentation

  override val searchTarget: PolySymbolSearchTarget?
    get() = if (config.isSearchTarget) PolySymbolSearchTarget.create(this) else null

  override val renameTarget: PolySymbolRenameTarget?
    get() = if (config.isRenameTarget) PolySymbolRenameTarget.create(this) else null

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> get(handle: DependencyHandle<T>): T =
    dependencyScope.resolved[(handle as DependencyHandleImpl<*>).index] as T

  override fun getDocumentationTarget(location: PsiElement?): DocumentationTarget? {
    val docBuilder = config.documentationBuilder
    if (docBuilder != null) {
      return PolySymbolDocumentationTarget.create(this, location, docBuilder)
    }
    return super.getDocumentationTarget(location)
  }

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
    config.navigationTargetsGetter?.let { dependencyScope.withinScope(project) { it(project) } }
    ?: super.getNavigationTargets(project)

  override fun matchContext(context: PolyContext): Boolean =
    config.matchContextGetter?.let { dependencyScope.withinScope(context) { it(context) } }
    ?: super.matchContext(context)

  override fun isEquivalentTo(symbol: Symbol): Boolean =
    super.isEquivalentTo(symbol)
    || (config.isEquivalentToGetter?.let { dependencyScope.withinScope(symbol) { it(symbol) } }
        ?: false)

  override fun <T : Any> get(property: PolySymbolProperty<T>): T? {
    val getter = config.propertyGetters[property]
    if (getter != null) {
      @Suppress("UNCHECKED_CAST")
      return property.tryCast(dependencyScope.withinScope { (getter as () -> T?).invoke() })
    }
    if (config.propertyValues.containsKey(property)) {
      return property.tryCast(config.propertyValues[property])
    }
    return super.get(property)
  }

  protected fun createPointerImpl(): Pointer<out PolySymbol> {
    if (dependencySource.isEmpty) return Pointer.hardPointer(this)
    val pointerSource = dependencySource.asFromPointers()
    val config = config
    val self: (BuiltConfig, DependencySource, DependencyScope) -> PolySymbol = buildConstructor()
    return Pointer {
      self(config, pointerSource, pointerSource.dependencyScope() ?: return@Pointer null)
    }
  }

  override fun equals(other: Any?): Boolean =
    other === this
    || other is BuiltPolySymbolBase
    && other.javaClass == javaClass
    && other.config.contentEquals(config)
    && other.dependencyScope.resolved == dependencyScope.resolved

  override fun hashCode(): Int {
    var result = config.contentHashCode()
    result = 31 * result + dependencyScope.resolved.hashCode()
    return result
  }
}

internal open class BuiltPolySymbolImpl(
  config: BuiltConfig,
  dependencySource: DependencySource,
  dependencyScope: DependencyScope,
  protected val psiContextGetter: (() -> PsiElement?)?,
) : BuiltPolySymbolBase(config, dependencySource, dependencyScope) {

  override val psiContext: PsiElement?
    get() = psiContextGetter?.let { this@BuiltPolySymbolImpl.dependencyScope.withinScope { it() } }
            ?: super.psiContext

  override fun buildConstructor(): (config: BuiltConfig, source: DependencySource, scope: DependencyScope) -> PolySymbol {
    val psiContextGetter = psiContextGetter
    return { config, source, scope ->
      BuiltPolySymbolImpl(config, source, scope, psiContextGetter)
    }
  }

  override fun createPointer(): Pointer<out PolySymbol> = createPointerImpl()
}

internal class BuiltPolySymbolWithPattern(
  config: BuiltConfig,
  dependencySource: DependencySource,
  dependencyScope: DependencyScope,
  private val patternBuilder: PolySymbolPatternBuilder.() -> Unit,
  psiContextGetter: (() -> PsiElement?)?,
) : BuiltPolySymbolImpl(config, dependencySource, dependencyScope, psiContextGetter), PolySymbolWithPattern {

  // Evaluate the pattern body lazily inside this instance's dependency scope,
  // so that any `by dependency(...)` handles declared on the builder read
  // fresh values in each read action.
  override val pattern: PolySymbolPattern by lazy(LazyThreadSafetyMode.PUBLICATION) {
    dependencyScope.withinScope { polySymbolPattern(patternBuilder) }
  }

  override fun buildConstructor(): (config: BuiltConfig, source: DependencySource, scope: DependencyScope) -> PolySymbol {
    val patternBuilder = patternBuilder
    val psiContextGetter = psiContextGetter
    return { config, source, scope ->
      BuiltPolySymbolWithPattern(config, source, scope, patternBuilder, psiContextGetter)
    }
  }

  override fun equals(other: Any?): Boolean =
    super.equals(other)
    && other is BuiltPolySymbolWithPattern
    && other.patternBuilder == patternBuilder

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + patternBuilder.javaClass.hashCode()
    return result
  }

  override fun createPointer(): Pointer<out PolySymbol> = createPointerImpl()
}

internal class BuiltPsiLinkedPolySymbol(
  config: BuiltConfig,
  dependencySource: DependencySource,
  dependencyScope: DependencyScope,
  private val sourceGetter: () -> PsiElement?,
) : BuiltPolySymbolBase(config, dependencySource, dependencyScope), PsiLinkedPolySymbol {

  override val linkedElement: PsiElement?
    get() = dependencyScope.withinScope { sourceGetter() }

  // Use PsiLinkedPolySymbol's default navigation / equivalence impls (they
  // read `source`) unless the builder explicitly supplied a custom getter.
  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
    if (config.navigationTargetsGetter != null)
      super<BuiltPolySymbolBase>.getNavigationTargets(project)
    else
      super<PsiLinkedPolySymbol>.getNavigationTargets(project)

  override fun isEquivalentTo(symbol: Symbol): Boolean =
    super<PsiLinkedPolySymbol>.isEquivalentTo(symbol)
    || super<BuiltPolySymbolBase>.isEquivalentTo(symbol)

  override fun buildConstructor(): (config: BuiltConfig, source: DependencySource, scope: DependencyScope) -> PolySymbol {
    val sourceGetter = sourceGetter
    return { config, source, scope ->
      BuiltPsiLinkedPolySymbol(config, source, scope, sourceGetter)
    }
  }

  override fun equals(other: Any?): Boolean =
    super.equals(other)
    && other is BuiltPsiLinkedPolySymbol
    && other.sourceGetter == sourceGetter

  override fun hashCode(): Int =
    super.hashCode() * 31 + sourceGetter.hashCode()

  @Suppress("UNCHECKED_CAST")
  override fun createPointer(): Pointer<out PsiLinkedPolySymbol> =
    createPointerImpl() as Pointer<out PsiLinkedPolySymbol>
}

internal class BuiltPolySymbolDeclaredInPsi(
  config: BuiltConfig,
  source: DependencySource,
  scope: DependencyScope,
  private val declarationSiteGetter: (() -> PolySymbolDeclarationSite?),
) : BuiltPolySymbolBase(config, source, scope), PolySymbolDeclaredInPsi {

  private val declarationSite: PolySymbolDeclarationSite? by lazy(LazyThreadSafetyMode.PUBLICATION) {
    scope.withinScope { declarationSiteGetter() }
  }

  override val sourceElement: PsiElement?
    get() = declarationSite?.sourceElement

  override val textRangeInSourceElement: TextRange?
    get() = declarationSite?.textRangeInSourceElement

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
    if (config.navigationTargetsGetter != null)
      super<BuiltPolySymbolBase>.getNavigationTargets(project)
    else
      super<PolySymbolDeclaredInPsi>.getNavigationTargets(project)


  override fun equals(other: Any?): Boolean =
    super.equals(other)
    && other is BuiltPolySymbolDeclaredInPsi
    && other.declarationSiteGetter == declarationSiteGetter

  override fun hashCode(): Int =
    super.hashCode() * 31 + declarationSiteGetter.hashCode()

  override fun buildConstructor(): (config: BuiltConfig, source: DependencySource, scope: DependencyScope) -> PolySymbol {
    val sourceGetter = declarationSiteGetter
    return { config, source, scope ->
      BuiltPolySymbolDeclaredInPsi(config, source, scope, sourceGetter)
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun createPointer(): Pointer<out PolySymbolDeclaredInPsi> =
    createPointerImpl() as Pointer<out PolySymbolDeclaredInPsi>
}
