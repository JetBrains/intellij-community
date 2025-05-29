// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.UserDataHolder
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.query.*
import com.intellij.webSymbols.query.impl.SearchMap
import com.intellij.webSymbols.utils.qualifiedKind
import com.intellij.webSymbols.utils.qualifiedName
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * Used when implementing a [PolySymbolsScope], which contains many elements.
 *
 * Caches the list of symbols and uses efficient cache to speed up queries. When extending the class,
 * you only need to override the initialize method and provide parameters to the super constructor to specify how results should be cached.
 */
abstract class PolySymbolsScopeWithCache<T : UserDataHolder, K>(
  /**
   * Allows to optimize for symbols with a particular [PolySymbolOrigin.framework].
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
  protected val key: K
) : PolySymbolsScope {

  /**
   * Called within a read action to initialize the scope's cache. Call [consumer] with all the
   * symbols within the scope.
   *
   * The results are cached in [dataHolder] using [CachedValue].
   * Add all the cache dependencies to [cacheDependencies] set.
   * If the results are going to be static for a particular [dataHolder]/[key] combination, add
   * [ModificationTracker.NEVER_CHANGED]. Note that [cacheDependencies] set cannot be empty.
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

  override fun hashCode(): Int =
    Objects.hash(framework, project, dataHolder, key)

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

  private fun createCachedSearchMap(namesProvider: WebSymbolNamesProvider): CachedValue<WebSymbolsSearchMap> =
    CachedValuesManager.getManager(project).createCachedValue {
      val dependencies = mutableSetOf<Any>()
      val map = WebSymbolsSearchMap(namesProvider, framework)
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

  override fun getMatchingSymbols(qualifiedName: PolySymbolQualifiedName,
                                  params: WebSymbolsNameMatchQueryParams,
                                  scope: Stack<PolySymbolsScope>): List<PolySymbol> =
    if ((params.queryExecutor.allowResolve || !requiresResolve)
        && (framework == null || params.framework == framework)
        && provides(qualifiedName.qualifiedKind)) {
      getMap(params.queryExecutor).getMatchingSymbols(qualifiedName, params, Stack(scope)).toList()
    }
    else emptyList()

  override fun getSymbols(qualifiedKind: PolySymbolQualifiedKind,
                          params: WebSymbolsListSymbolsQueryParams,
                          scope: Stack<PolySymbolsScope>): List<PolySymbolsScope> =
    if ((params.queryExecutor.allowResolve || !requiresResolve)
        && (framework == null || params.framework == framework)
        && provides(qualifiedKind)) {
      getMap(params.queryExecutor).getSymbols(qualifiedKind, params).toList()
    }
    else emptyList()

  override fun getCodeCompletions(qualifiedName: PolySymbolQualifiedName,
                                  params: WebSymbolsCodeCompletionQueryParams,
                                  scope: Stack<PolySymbolsScope>): List<WebSymbolCodeCompletionItem> =
    if ((params.queryExecutor.allowResolve || !requiresResolve)
        && (framework == null || params.framework == framework)
        && provides(qualifiedName.qualifiedKind)) {
      getMap(params.queryExecutor).getCodeCompletions(qualifiedName, params, Stack(scope)).toList()
    }
    else emptyList()

  private fun getMap(queryExecutor: WebSymbolsQueryExecutor): WebSymbolsSearchMap =
    getNamesProviderToMapCache().getOrCreateMap(queryExecutor.namesProvider, this::createCachedSearchMap)

  private class NamesProviderToMapCache {
    private val cache: ConcurrentMap<WebSymbolNamesProvider, CachedValue<WebSymbolsSearchMap>> = ContainerUtil.createConcurrentSoftKeySoftValueMap()
    private var cacheMisses = 0

    fun getOrCreateMap(namesProvider: WebSymbolNamesProvider,
                       createCachedSearchMap: (namesProvider: WebSymbolNamesProvider) -> CachedValue<WebSymbolsSearchMap>): WebSymbolsSearchMap {
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

  private class WebSymbolsSearchMap(namesProvider: WebSymbolNamesProvider, private val framework: FrameworkId?)
    : SearchMap<PolySymbol>(namesProvider) {

    override fun Sequence<PolySymbol>.mapAndFilter(params: WebSymbolsQueryParams): Sequence<PolySymbol> = this

    fun add(symbol: PolySymbol) {
      assert(framework == null || symbol.origin.framework == framework || symbol.origin.framework == null) {
        "WebSymbolsScope only accepts symbols with framework: $framework, but symbol with framework ${symbol.origin.framework} was added."
      }
      add(symbol.qualifiedName, symbol.pattern, symbol)
    }

  }

}