// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.impl

import com.intellij.model.Pointer
import com.intellij.polySymbols.*
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.patterns.PolySymbolPattern
import com.intellij.polySymbols.query.*
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.ConcurrentHashMap

@Internal
abstract class StaticPolySymbolScopeBase<Root : Any, Contribution : Any, Origin : PolySymbolOrigin> : StaticPolySymbolScope {

  private val namesProviderCache: MutableMap<PolySymbolNamesProvider, NameProvidersCache> = ContainerUtil.createConcurrentSoftKeySoftValueMap()
  private var namesProviderCacheMisses = 0

  private val queryExecutorCache: MutableMap<PolySymbolQueryExecutor, QueryExecutorContributionsCache> = ContainerUtil.createConcurrentSoftKeySoftValueMap()
  private var queryExecutorCacheMisses = 0

  private val roots = mutableMapOf<Root, Origin>()

  @JvmField
  protected var modCount: Long = 0

  abstract override fun createPointer(): Pointer<out StaticPolySymbolScopeBase<Root, Contribution, Origin>>

  final override fun getModificationCount(): Long =
    modCount

  final override fun getMatchingSymbols(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolNameMatchQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbol> =
    getMaps(params).flatMap {
      it.getMatchingSymbols(qualifiedName, params, stack.copy())
    }.toList()

  final override fun getSymbols(
    qualifiedKind: PolySymbolQualifiedKind,
    params: PolySymbolListSymbolsQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbol> =
    getMaps(params).flatMap {
      it.getSymbols(qualifiedKind, params)
    }.toList()

  final override fun getCodeCompletions(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolCodeCompletionQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbolCodeCompletionItem> =
    getMaps(params).flatMap {
      it.getCodeCompletions(qualifiedName, params, stack.copy())
    }.toList()

  internal fun getMatchingSymbols(
    contribution: Contribution,
    origin: Origin,
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolNameMatchQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbol> =
    getMap(params.queryExecutor, contribution, origin)
      .getMatchingSymbols(qualifiedName, params, stack)
      .toList()

  internal fun getSymbols(
    contribution: Contribution,
    origin: Origin,
    qualifiedKind: PolySymbolQualifiedKind,
    params: PolySymbolListSymbolsQueryParams,
  ): List<PolySymbol> =
    getMap(params.queryExecutor, contribution, origin)
      .getSymbols(qualifiedKind, params)
      .toList()

  internal fun getCodeCompletions(
    contribution: Contribution,
    origin: Origin,
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolCodeCompletionQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbolCodeCompletionItem> =
    getMap(params.queryExecutor, contribution, origin)
      .getCodeCompletions(qualifiedName, params, stack)
      .toList()


  protected open fun dropCaches() {
    namesProviderCache.clear()
    queryExecutorCache.clear()
  }

  protected abstract fun matchContext(origin: Origin, context: PolyContext): Boolean

  private fun getMaps(params: PolySymbolQueryParams): Sequence<ContributionSearchMap> =
    roots.asSequence()
      .filter { (_, origin) -> matchContext(origin, params.queryExecutor.context) }
      .map { (contributions, origin) ->
        getMapForRoot(params.queryExecutor, contributions, origin)
      }

  protected abstract fun adaptAllContributions(
    contribution: Contribution,
    framework: FrameworkId?,
    origin: Origin,
  ): Sequence<StaticSymbolContributionAdapter>

  protected abstract fun adaptAllRootContributions(
    root: Root,
    framework: FrameworkId?,
    origin: Origin,
  ): Sequence<StaticSymbolContributionAdapter>

  private fun getMap(
    queryExecutor: PolySymbolQueryExecutor,
    contribution: Contribution,
    origin: Origin,
  ): ContributionSearchMap =
    getOrCreateMap(queryExecutor, contribution) { consumer ->
      adaptAllContributions(contribution, origin.framework, origin).forEach(consumer)
    }


  private fun getMapForRoot(
    queryExecutor: PolySymbolQueryExecutor,
    root: Root,
    origin: Origin,
  ): ContributionSearchMap =
    getOrCreateMap(queryExecutor, root) { consumer ->
      adaptAllRootContributions(root, origin.framework, origin).forEach(consumer)
    }

  private fun getOrCreateMap(
    queryExecutor: PolySymbolQueryExecutor,
    key: Any,
    mapInitializer: (consumer: (StaticSymbolContributionAdapter) -> Unit) -> Unit,
  ): ContributionSearchMap =
    getNameProvidersCache(queryExecutor.namesProvider).getOrCreateMap(key, mapInitializer)

  private fun getNameProvidersCache(namesProvider: PolySymbolNamesProvider): NameProvidersCache {
    if (namesProviderCacheMisses > 100) {
      // Get rid of old soft keys
      namesProviderCacheMisses = 0
      namesProviderCache.clear()
    }
    return namesProviderCache.computeIfAbsent(namesProvider) {
      namesProviderCacheMisses++; NameProvidersCache(namesProvider)
    }
      .also { it.checkForModifications() }
  }

  private fun getQueryExecutorContributionsCache(queryExecutor: PolySymbolQueryExecutor): QueryExecutorContributionsCache {
    if (queryExecutorCacheMisses > 100) {
      // Get rid of old soft keys
      queryExecutorCacheMisses = 0
      queryExecutorCache.clear()
    }
    return queryExecutorCache.computeIfAbsent(queryExecutor) { queryExecutorCacheMisses++; QueryExecutorContributionsCache(queryExecutor) }
      .also { it.checkForModifications() }
  }

  internal fun addRoot(root: Root?, origin: Origin) {
    modCount++
    if (root == null) return
    roots[root] = origin
  }

  internal fun removeRoot(root: Root?) {
    modCount++
    roots.remove(root)
  }

  internal fun getRootOrigin(root: Root): Origin? =
    roots[root]

  interface StaticSymbolContributionAdapter {
    val qualifiedKind: PolySymbolQualifiedKind
    val name: String
    val pattern: PolySymbolPattern?
    val framework: FrameworkId?
    fun withQueryExecutorContext(queryExecutor: PolySymbolQueryExecutor): PolySymbol
    fun matchContext(context: PolyContext): Boolean =
      framework == null || context.framework == null || context.framework == framework
  }

  private inner class ContributionSearchMap(namesProvider: PolySymbolNamesProvider)
    : SearchMap<StaticSymbolContributionAdapter>(namesProvider) {

    fun add(item: StaticSymbolContributionAdapter) {
      add(item.qualifiedKind.withName(item.name), item.pattern, item)
    }

    override fun Sequence<StaticSymbolContributionAdapter>.mapAndFilter(params: PolySymbolQueryParams): Sequence<PolySymbol> {
      val cache = getQueryExecutorContributionsCache(params.queryExecutor)
      return filter { it.matchContext(params.queryExecutor.context) }
        .map { cache.getOrCreateSymbol(it) }
    }

  }

  private inner class NameProvidersCache(private val namesProvider: PolySymbolNamesProvider) {
    private val mapsCache: MutableMap<Any, ContributionSearchMap> = ConcurrentHashMap()
    private var namesProviderTimestamp: Long = -1

    fun getOrCreateMap(
      key: Any,
      mapInitializer: (consumer: (StaticSymbolContributionAdapter) -> Unit) -> Unit,
    ): ContributionSearchMap =
      mapsCache.getOrPut(key) {
        ContributionSearchMap(namesProvider)
          .also { mapInitializer(it::add) }
      }

    fun checkForModifications() {
      if (namesProvider.modificationCount != this.namesProviderTimestamp) {
        synchronized(this) {
          if (namesProvider.modificationCount != this.namesProviderTimestamp) {
            mapsCache.clear()
            this.namesProviderTimestamp = namesProvider.modificationCount
          }
        }
      }
    }
  }

  private inner class QueryExecutorContributionsCache(private val queryExecutor: PolySymbolQueryExecutor) {
    private val symbolsCache: MutableMap<StaticSymbolContributionAdapter, PolySymbol> = ConcurrentHashMap()
    private var queryExecutorModificationCount: Long = -1

    fun getOrCreateSymbol(item: StaticSymbolContributionAdapter): PolySymbol =
      symbolsCache.getOrPut(item) { item.withQueryExecutorContext(queryExecutor) }

    fun checkForModifications() {
      if (queryExecutor.modificationCount != this.queryExecutorModificationCount) {
        synchronized(this) {
          if (queryExecutor.modificationCount != this.queryExecutorModificationCount) {
            symbolsCache.clear()
            this.queryExecutorModificationCount = queryExecutor.modificationCount
          }
        }
      }
    }

  }

}
