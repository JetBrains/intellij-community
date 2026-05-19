// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query.impl

import com.intellij.model.Pointer
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolBuilder
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.impl.SearchMap
import com.intellij.polySymbols.impl.checkNoPsiCapture
import com.intellij.polySymbols.polySymbol
import com.intellij.polySymbols.query.PolySymbolCodeCompletionQueryParams
import com.intellij.polySymbols.utils.ReferencingPolySymbol
import com.intellij.polySymbols.query.PolySymbolListSymbolsQueryParams
import com.intellij.polySymbols.query.PolySymbolNameMatchQueryParams
import com.intellij.polySymbols.query.PolySymbolNamesProvider
import com.intellij.polySymbols.query.PolySymbolQueryParams
import com.intellij.polySymbols.query.PolySymbolQueryStack
import com.intellij.polySymbols.query.PolySymbolScope
import com.intellij.polySymbols.query.PolySymbolScopeBuilder
import com.intellij.polySymbols.query.PolySymbolScopeInitializer
import com.intellij.polySymbols.query.PolySymbolWithPattern
import com.intellij.polySymbols.utils.getDefaultCodeCompletions
import com.intellij.polySymbols.utils.qualifiedName
import com.intellij.util.containers.ContainerUtil
import java.util.concurrent.ConcurrentMap

private const val LINEAR_THRESHOLD = 5

/**
 * Non-cached [PolySymbolScope] backed by a lazily-materialized symbol list.
 *
 * Symbols are produced by [symbolsSupplier] on first access (`getSymbols` /
 * `getMatchingSymbols` / `getCodeCompletions` / `createPointer`). Below
 * [LINEAR_THRESHOLD] symbols queries are served by a linear scan; above it, a
 * per-[PolySymbolNamesProvider] [SearchMap] is lazily populated.
 */
internal class BasicPolySymbolScope(
  private val providesKinds: Set<PolySymbolKind>,
  private val exclusiveForKinds: Set<PolySymbolKind>,
  private val exclusiveForPredicate: ((PolySymbolKind) -> Boolean)?,
  private val requiresResolveValue: Boolean,
  private val codeCompletionFilter: ((PolySymbolKind, List<PolySymbolCodeCompletionItem>) -> List<PolySymbolCodeCompletionItem>)?,
  private val nameMatchFilter: ((PolySymbolQualifiedName, List<PolySymbol>) -> List<PolySymbol>)?,
  private val symbolsSupplier: () -> List<PolySymbol>,
) : PolySymbolScope {

  private val symbols: List<PolySymbol> by lazy(LazyThreadSafetyMode.PUBLICATION) { symbolsSupplier() }

  private val mapCache: ConcurrentMap<PolySymbolNamesProvider, BasicSearchMap>? by lazy(LazyThreadSafetyMode.PUBLICATION) {
    if (symbols.size >= LINEAR_THRESHOLD)
      ContainerUtil.createConcurrentSoftKeySoftValueMap()
    else null
  }

  private fun provides(kind: PolySymbolKind): Boolean = kind in providesKinds

  override fun isExclusiveFor(kind: PolySymbolKind): Boolean =
    kind in exclusiveForKinds || exclusiveForPredicate?.invoke(kind) == true

  override fun createPointer(): Pointer<BasicPolySymbolScope> {
    val symbolPtrs = symbols.map { it.createPointer() }
    val providesKinds = providesKinds
    val exclusiveForKinds = exclusiveForKinds
    val exclusiveForPredicate = exclusiveForPredicate
    val requiresResolveValue = requiresResolveValue
    val codeCompletionFilter = codeCompletionFilter
    val nameMatchFilter = nameMatchFilter
    return Pointer {
      val derefed = symbolPtrs.mapNotNull { it.dereference() }
      if (derefed.size != symbolPtrs.size) return@Pointer null
      BasicPolySymbolScope(
        providesKinds = providesKinds,
        exclusiveForKinds = exclusiveForKinds,
        exclusiveForPredicate = exclusiveForPredicate,
        requiresResolveValue = requiresResolveValue,
        codeCompletionFilter = codeCompletionFilter,
        nameMatchFilter = nameMatchFilter,
        symbolsSupplier = { derefed },
      )
    }
  }

  override fun getSymbols(
    kind: PolySymbolKind,
    params: PolySymbolListSymbolsQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbol> {
    if (requiresResolveValue && !params.queryExecutor.allowResolve) return emptyList()
    if (!provides(kind)) return emptyList()
    val mapCache = this.mapCache
    return if (mapCache == null)
      symbols.filter { it.kind == kind }
    else
      getMap(mapCache, params.queryExecutor.namesProvider).getSymbols(kind, params).toList()
  }

  override fun getMatchingSymbols(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolNameMatchQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbol> {
    if (requiresResolveValue && !params.queryExecutor.allowResolve) return emptyList()
    if (!provides(qualifiedName.kind)) return emptyList()
    val mapCache = this.mapCache
    val base = if (mapCache == null)
      super.getMatchingSymbols(qualifiedName, params, stack)
    else
      getMap(mapCache, params.queryExecutor.namesProvider)
        .getMatchingSymbols(qualifiedName, params, stack.copy())
        .toList()
    val filter = nameMatchFilter ?: return base
    return filter(qualifiedName, base)
  }

  override fun getCodeCompletions(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolCodeCompletionQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbolCodeCompletionItem> {
    if (requiresResolveValue && !params.queryExecutor.allowResolve) return emptyList()
    if (!provides(qualifiedName.kind)) return emptyList()
    val mapCache = this.mapCache
    val base = if (mapCache == null)
      getDefaultCodeCompletions(qualifiedName, params, stack)
    else
      getMap(mapCache, params.queryExecutor.namesProvider)
        .getCodeCompletions(qualifiedName, params, stack.copy())
        .toList()
    val filter = codeCompletionFilter ?: return base
    return filter(qualifiedName.kind, base)
  }

  private fun getMap(
    cache: ConcurrentMap<PolySymbolNamesProvider, BasicSearchMap>,
    namesProvider: PolySymbolNamesProvider,
  ): BasicSearchMap =
    cache.getOrPut(namesProvider) {
      BasicSearchMap(namesProvider).also { map ->
        symbols.forEach { map.add(it) }
      }
    }

  private class BasicSearchMap(namesProvider: PolySymbolNamesProvider) : SearchMap<PolySymbol>(namesProvider) {
    override fun Sequence<PolySymbol>.mapAndFilter(params: PolySymbolQueryParams): Sequence<PolySymbol> = this

    fun add(symbol: PolySymbol) {
      add(symbol.qualifiedName, (symbol as? PolySymbolWithPattern)?.pattern, symbol)
    }
  }
}

internal class PolySymbolScopeInitializerImpl : PolySymbolScopeInitializer {

  private val _symbols: MutableList<PolySymbol> = mutableListOf()
  val symbols: List<PolySymbol> get() = _symbols

  override fun add(symbol: PolySymbol) {
    _symbols += symbol
  }

  override fun addAll(symbols: Iterable<PolySymbol>) {
    _symbols += symbols
  }

  override fun PolySymbol.unaryPlus() {
    _symbols += this
  }

  override fun Iterable<PolySymbol>.unaryPlus() {
    _symbols += this
  }

  override fun addSymbol(
    kind: PolySymbolKind,
    name: String,
    body: PolySymbolBuilder.() -> Unit,
  ) {
    _symbols += polySymbol(kind, name, body)
  }

  override fun referenceSymbols(
    kind: PolySymbolKind,
    displayName: String,
    vararg referencedKinds: PolySymbolKind,
    priority: PolySymbol.Priority?,
  ) {
    _symbols += ReferencingPolySymbol.create(kind, displayName, *referencedKinds, priority = priority)
  }
}

internal class PolySymbolScopeBuilderImpl : PolySymbolScopeBuilder {

  private val providesKinds: MutableSet<PolySymbolKind> = mutableSetOf()
  private val exclusiveForKinds: MutableSet<PolySymbolKind> = mutableSetOf()
  private var exclusiveForPredicate: ((PolySymbolKind) -> Boolean)? = null
  private var requiresResolveValue: Boolean = true
  private var initBody: (PolySymbolScopeInitializer.() -> Unit)? = null
  private var codeCompletionFilter: ((PolySymbolKind, List<PolySymbolCodeCompletionItem>) -> List<PolySymbolCodeCompletionItem>)? = null
  private var nameMatchFilter: ((PolySymbolQualifiedName, List<PolySymbol>) -> List<PolySymbol>)? = null

  override fun provides(vararg kinds: PolySymbolKind) {
    providesKinds.addAll(kinds)
  }

  override fun provides(kinds: Collection<PolySymbolKind>) {
    providesKinds.addAll(kinds)
  }

  override fun exclusiveFor(vararg kinds: PolySymbolKind) {
    exclusiveForKinds.addAll(kinds)
  }

  override fun exclusiveFor(kinds: Collection<PolySymbolKind>) {
    exclusiveForKinds.addAll(kinds)
  }

  override fun exclusiveFor(predicate: (PolySymbolKind) -> Boolean) {
    checkNoPsiCapture(predicate, "polySymbolScope.exclusiveFor")
    exclusiveForPredicate = predicate
  }

  override fun requiresResolve(value: Boolean) {
    requiresResolveValue = value
  }

  override fun filterCodeCompletions(
    filter: (kind: PolySymbolKind, items: List<PolySymbolCodeCompletionItem>) -> List<PolySymbolCodeCompletionItem>,
  ) {
    checkNoPsiCapture(filter, "polySymbolScope.filterCodeCompletions")
    codeCompletionFilter = filter
  }

  override fun filterNameMatches(
    filter: (name: PolySymbolQualifiedName, matches: List<PolySymbol>) -> List<PolySymbol>,
  ) {
    checkNoPsiCapture(filter, "polySymbolScope.filterNameMatches")
    nameMatchFilter = filter
  }

  override fun initialize(body: PolySymbolScopeInitializer.() -> Unit) {
    check(initBody == null) { "polySymbolScope: initialize { } must be called exactly once." }
    checkNoPsiCapture(body, "polySymbolScope.initialize")
    initBody = body
  }

  fun build(): PolySymbolScope {
    val body = initBody ?: error("polySymbolScope: initialize { } was not called.")
    check(providesKinds.isNotEmpty()) { "polySymbolScope: provides() must be called with at least one kind." }
    val frozenKinds = providesKinds.toHashSet()
    val codeCompletionFilter = codeCompletionFilter
    val nameMatchFilter = nameMatchFilter
    return BasicPolySymbolScope(
      providesKinds = frozenKinds,
      exclusiveForKinds = exclusiveForKinds.toHashSet(),
      exclusiveForPredicate = exclusiveForPredicate,
      requiresResolveValue = requiresResolveValue,
      codeCompletionFilter = codeCompletionFilter,
      nameMatchFilter = nameMatchFilter,
      symbolsSupplier = {
        val initializer = PolySymbolScopeInitializerImpl()
        body.invoke(initializer)
        val symbols = initializer.symbols
        symbols.find { it.kind !in frozenKinds }?.let {
          throw IllegalArgumentException(
            "Symbol $it kind ${it.kind} is not provided by this scope. " +
            "Use `provides` to specify supported symbol kinds."
          )
        }
        symbols
      },
    )
  }
}

internal fun buildPolySymbolScope(
  configure: PolySymbolScopeBuilder.() -> Unit,
): PolySymbolScope {
  checkNoPsiCapture(configure, "polySymbolScope.configure")
  return PolySymbolScopeBuilderImpl().apply(configure).build()
}
