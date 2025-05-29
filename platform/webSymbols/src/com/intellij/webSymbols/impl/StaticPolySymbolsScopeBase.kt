// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.impl

import com.intellij.model.Pointer
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.*
import com.intellij.webSymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.webSymbols.context.PolyContext
import com.intellij.webSymbols.patterns.PolySymbolsPattern
import com.intellij.webSymbols.query.*
import com.intellij.webSymbols.query.impl.SearchMap
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.ConcurrentHashMap

@Internal
abstract class StaticPolySymbolsScopeBase<Root : Any, Contribution : Any, Origin : PolySymbolOrigin> : StaticPolySymbolsScope {

  private val namesProviderCache: MutableMap<WebSymbolNamesProvider, NameProvidersCache> = ContainerUtil.createConcurrentSoftKeySoftValueMap()
  private var namesProviderCacheMisses = 0

  private val queryExecutorCache: MutableMap<WebSymbolsQueryExecutor, QueryExecutorContributionsCache> = ContainerUtil.createConcurrentSoftKeySoftValueMap()
  private var queryExecutorCacheMisses = 0

  private val roots = mutableMapOf<Root, Origin>()

  @JvmField
  protected var modCount: Long = 0

  abstract override fun createPointer(): Pointer<out StaticPolySymbolsScopeBase<Root, Contribution, Origin>>

  final override fun getModificationCount(): Long =
    modCount

  final override fun getMatchingSymbols(qualifiedName: PolySymbolQualifiedName,
                                        params: WebSymbolsNameMatchQueryParams,
                                        scope: Stack<PolySymbolsScope>): List<PolySymbol> =
    getMaps(params).flatMap {
      it.getMatchingSymbols(qualifiedName, params, Stack(scope))
    }.toList()

  final override fun getSymbols(qualifiedKind: PolySymbolQualifiedKind,
                                params: WebSymbolsListSymbolsQueryParams,
                                scope: Stack<PolySymbolsScope>): List<PolySymbolsScope> =
    getMaps(params).flatMap {
      it.getSymbols(qualifiedKind, params)
    }.toList()

  final override fun getCodeCompletions(qualifiedName: PolySymbolQualifiedName,
                                        params: WebSymbolsCodeCompletionQueryParams,
                                        scope: Stack<PolySymbolsScope>): List<PolySymbolCodeCompletionItem> =
    getMaps(params).flatMap {
      it.getCodeCompletions(qualifiedName, params, Stack(scope))
    }.toList()

  internal fun getMatchingSymbols(contribution: Contribution,
                                  origin: Origin,
                                  qualifiedName: PolySymbolQualifiedName,
                                  params: WebSymbolsNameMatchQueryParams,
                                  scopeStack: Stack<PolySymbolsScope>): List<PolySymbol> =
    getMap(params.queryExecutor, contribution, origin)
      .getMatchingSymbols(qualifiedName, params, scopeStack)
      .toList()

  internal fun getSymbols(contribution: Contribution,
                          origin: Origin,
                          qualifiedKind: PolySymbolQualifiedKind,
                          params: WebSymbolsListSymbolsQueryParams): List<PolySymbolsScope> =
    getMap(params.queryExecutor, contribution, origin)
      .getSymbols(qualifiedKind, params)
      .toList()

  internal fun getCodeCompletions(contribution: Contribution,
                                  origin: Origin,
                                  qualifiedName: PolySymbolQualifiedName,
                                  params: WebSymbolsCodeCompletionQueryParams,
                                  scopeStack: Stack<PolySymbolsScope>): List<PolySymbolCodeCompletionItem> =
    getMap(params.queryExecutor, contribution, origin)
      .getCodeCompletions(qualifiedName, params, scopeStack)
      .toList()


  protected open fun dropCaches() {
    namesProviderCache.clear()
    queryExecutorCache.clear()
  }

  protected abstract fun matchContext(origin: Origin, context: PolyContext): Boolean

  private fun getMaps(params: WebSymbolsQueryParams): Sequence<ContributionSearchMap> =
    roots.asSequence()
      .filter { (_, origin) -> matchContext(origin, params.queryExecutor.context) }
      .map { (contributions, origin) ->
        getMapForRoot(params.queryExecutor, contributions, origin)
      }

  protected abstract fun adaptAllContributions(contribution: Contribution,
                                               framework: FrameworkId?,
                                               origin: Origin): Sequence<StaticSymbolContributionAdapter>

  protected abstract fun adaptAllRootContributions(root: Root,
                                                   framework: FrameworkId?,
                                                   origin: Origin): Sequence<StaticSymbolContributionAdapter>

  private fun getMap(queryExecutor: WebSymbolsQueryExecutor,
                     contribution: Contribution,
                     origin: Origin): ContributionSearchMap =
    getOrCreateMap(queryExecutor, contribution) { consumer ->
      adaptAllContributions(contribution, origin.framework, origin).forEach(consumer)
    }


  private fun getMapForRoot(queryExecutor: WebSymbolsQueryExecutor,
                            root: Root,
                            origin: Origin): ContributionSearchMap =
    getOrCreateMap(queryExecutor, root) { consumer ->
      adaptAllRootContributions(root, origin.framework, origin).forEach(consumer)
    }

  private fun getOrCreateMap(queryExecutor: WebSymbolsQueryExecutor,
                             key: Any,
                             mapInitializer: (consumer: (StaticSymbolContributionAdapter) -> Unit) -> Unit): ContributionSearchMap =
    getNameProvidersCache(queryExecutor.namesProvider).getOrCreateMap(key, mapInitializer)

  private fun getNameProvidersCache(namesProvider: WebSymbolNamesProvider): NameProvidersCache {
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

  private fun getQueryExecutorContributionsCache(queryExecutor: WebSymbolsQueryExecutor): QueryExecutorContributionsCache {
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
    val namespace: SymbolNamespace
    val kind: String
    val name: String
    val pattern: PolySymbolsPattern?
    val framework: FrameworkId?
    fun withQueryExecutorContext(queryExecutor: WebSymbolsQueryExecutor): PolySymbol
    fun matchContext(context: PolyContext): Boolean =
      framework == null || context.framework == null || context.framework == framework
  }

  private inner class ContributionSearchMap(namesProvider: WebSymbolNamesProvider)
    : SearchMap<StaticSymbolContributionAdapter>(namesProvider) {

    fun add(item: StaticSymbolContributionAdapter) {
      add(PolySymbolQualifiedName(item.namespace, item.kind, item.name), item.pattern, item)
    }

    override fun Sequence<StaticSymbolContributionAdapter>.mapAndFilter(params: WebSymbolsQueryParams): Sequence<PolySymbol> {
      val cache = getQueryExecutorContributionsCache(params.queryExecutor)
      return filter { it.matchContext(params.queryExecutor.context) }
        .map { cache.getOrCreateSymbol(it) }
    }

  }

  private inner class NameProvidersCache(private val namesProvider: WebSymbolNamesProvider) {
    private val mapsCache: MutableMap<Any, ContributionSearchMap> = ConcurrentHashMap()
    private var namesProviderTimestamp: Long = -1

    fun getOrCreateMap(key: Any,
                       mapInitializer: (consumer: (StaticSymbolContributionAdapter) -> Unit) -> Unit): ContributionSearchMap =
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

  private inner class QueryExecutorContributionsCache(private val queryExecutor: WebSymbolsQueryExecutor) {
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
