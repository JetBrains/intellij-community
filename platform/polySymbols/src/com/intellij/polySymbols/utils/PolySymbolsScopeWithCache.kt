// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.utils

import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.UserDataHolder
import com.intellij.polySymbols.FrameworkId
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.PolySymbolsScope
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.query.PolySymbolNamesProvider
import com.intellij.polySymbols.query.PolySymbolsCodeCompletionQueryParams
import com.intellij.polySymbols.query.PolySymbolsListSymbolsQueryParams
import com.intellij.polySymbols.query.PolySymbolsNameMatchQueryParams
import com.intellij.polySymbols.query.PolySymbolsQueryExecutor
import com.intellij.polySymbols.query.PolySymbolsQueryParams
import com.intellij.polySymbols.impl.SearchMap
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.Stack
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * Used when implementing a [com.intellij.polySymbols.PolySymbolsScope], which contains many elements.
 *
 * Caches the list of symbols and uses efficient cache to speed up queries. When extending the class,
 * you only need to override the initialize method and provide parameters to the super constructor to specify how results should be cached.
 */
abstract class PolySymbolsScopeWithCache<T : UserDataHolder, K>(
  /**
   * Allows to optimize for symbols with a particular [com.intellij.polySymbols.PolySymbolOrigin.framework].
   * If `null` all symbols will be accepted and scope will be queried in all contexts.
   */
  protected val framework: FrameworkId?,
  protected val project: Project,
  /**
   * The holder of the scope's cache.
   */
  protected val dataHolder: T,
  /**
   * Additional discriminator for different scopes on the same `dataHolder`.
   * You can provide `Unit`, which would mean that there is only one scope of particular class.
   */
  protected val key: K,
) : PolySymbolsScope {

  /**
   * Called within a read action to initialize the scope's cache. Call [consumer] with all the
   * symbols within the scope.
   *
   * The results are cached in [dataHolder] using [com.intellij.psi.util.CachedValue].
   * Add all the cache dependencies to [cacheDependencies] set.
   * If the results are going to be static for a particular [dataHolder]/[key] combination, add
   * [com.intellij.openapi.util.ModificationTracker.NEVER_CHANGED]. Note that [cacheDependencies] set cannot be empty.
   */
  protected abstract fun initialize(consumer: (PolySymbol) -> Unit, cacheDependencies: MutableSet<Any>)

  /**
   * Allows optimizing queries and to avoid scope initialization.
   * Return `false` if particular symbol kind cannot be provided by the scope.
   */
  protected abstract fun provides(qualifiedKind: PolySymbolQualifiedKind): Boolean

  abstract override fun createPointer(): Pointer<out PolySymbolsScopeWithCache<T, K>>

  private val requiresResolve: Boolean get() = true

  override fun getModificationCount(): Long =
    PsiModificationTracker.getInstance(project).modificationCount

  override fun equals(other: Any?): Boolean =
    other === this
    || (other != null
        && other is PolySymbolsScopeWithCache<*, *>
        && other::class.java == this::class.java
        && other.framework == framework
        && other.key == key
        && other.project == project
        && other.dataHolder == dataHolder)

  override fun hashCode(): Int {
    var result = 31
    result = 31 * result + framework.hashCode()
    result = 31 * result + project.hashCode()
    result = 31 * result + dataHolder.hashCode()
    result = 31 * result + key.hashCode()
    return result
  }

  private fun getNamesProviderToMapCache(): NamesProviderToMapCache {
    val manager = CachedValuesManager.getManager(project)
    return if (key == Unit) {
      manager.getCachedValue(dataHolder, manager.getKeyForClass(this::class.java), {
        CachedValueProvider.Result(NamesProviderToMapCache(), PsiModificationTracker.NEVER_CHANGED)
      }, false)
    }
    else {
      manager.getCachedValue(dataHolder, manager.getKeyForClass(this::class.java), {
        CachedValueProvider.Result(ConcurrentHashMap<K, NamesProviderToMapCache>(), ModificationTracker.NEVER_CHANGED)
      }, false).getOrPut(key) { NamesProviderToMapCache() }
    }
  }

  private fun createCachedSearchMap(namesProvider: PolySymbolNamesProvider): CachedValue<PolySymbolsSearchMap> =
    CachedValuesManager.getManager(project).createCachedValue {
      val dependencies = mutableSetOf<Any>()
      val map = PolySymbolsSearchMap(namesProvider, framework)
      initialize(
        {
          if (!provides(it.qualifiedKind))
            throw IllegalArgumentException("Web Symbol with unsupported kind: ${it.qualifiedKind} added. $it")
          map.add(it)
        }, dependencies)
      if (dependencies.isEmpty()) {
        throw IllegalArgumentException(
          "CacheDependencies cannot be empty. Failed to initialize $javaClass. Add ModificationTracker.NEVER_CHANGED if cache should never be dropped.")
      }
      dependencies.add(namesProvider)
      CachedValueProvider.Result.create(map, dependencies.toList())
    }

  override fun getMatchingSymbols(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolsNameMatchQueryParams,
    scope: Stack<PolySymbolsScope>,
  ): List<PolySymbol> =
    if ((params.queryExecutor.allowResolve || !requiresResolve)
        && (framework == null || params.framework == framework)
        && provides(qualifiedName.qualifiedKind)) {
      getMap(params.queryExecutor).getMatchingSymbols(qualifiedName, params, Stack(scope)).toList()
    }
    else emptyList()

  override fun getSymbols(
    qualifiedKind: PolySymbolQualifiedKind,
    params: PolySymbolsListSymbolsQueryParams,
    scope: Stack<PolySymbolsScope>,
  ): List<PolySymbolsScope> =
    if ((params.queryExecutor.allowResolve || !requiresResolve)
        && (framework == null || params.framework == framework)
        && provides(qualifiedKind)) {
      getMap(params.queryExecutor).getSymbols(qualifiedKind, params).toList()
    }
    else emptyList()

  override fun getCodeCompletions(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolsCodeCompletionQueryParams,
    scope: Stack<PolySymbolsScope>,
  ): List<PolySymbolCodeCompletionItem> =
    if ((params.queryExecutor.allowResolve || !requiresResolve)
        && (framework == null || params.framework == framework)
        && provides(qualifiedName.qualifiedKind)) {
      getMap(params.queryExecutor).getCodeCompletions(qualifiedName, params, Stack(scope)).toList()
    }
    else emptyList()

  private fun getMap(queryExecutor: PolySymbolsQueryExecutor): PolySymbolsSearchMap =
    getNamesProviderToMapCache().getOrCreateMap(queryExecutor.namesProvider, this::createCachedSearchMap)

  private class NamesProviderToMapCache {
    private val cache: ConcurrentMap<PolySymbolNamesProvider, CachedValue<PolySymbolsSearchMap>> = ContainerUtil.createConcurrentSoftKeySoftValueMap()
    private var cacheMisses = 0

    fun getOrCreateMap(
      namesProvider: PolySymbolNamesProvider,
      createCachedSearchMap: (namesProvider: PolySymbolNamesProvider) -> CachedValue<PolySymbolsSearchMap>,
    ): PolySymbolsSearchMap {
      if (cacheMisses > 20) {
        // Get rid of old soft keys
        cacheMisses = 0
        cache.clear()
      }
      return cache.getOrPut(namesProvider) {
        cacheMisses++
        createCachedSearchMap(namesProvider)
      }.value
    }
  }

  private class PolySymbolsSearchMap(namesProvider: PolySymbolNamesProvider, private val framework: FrameworkId?)
    : SearchMap<PolySymbol>(namesProvider) {

    override fun Sequence<PolySymbol>.mapAndFilter(params: PolySymbolsQueryParams): Sequence<PolySymbol> = this

    fun add(symbol: PolySymbol) {
      assert(framework == null || symbol.origin.framework == framework || symbol.origin.framework == null) {
        "PolySymbolsScope only accepts symbols with framework: $framework, but symbol with framework ${symbol.origin.framework} was added."
      }
      add(symbol.qualifiedName, symbol.pattern, symbol)
    }

  }

}