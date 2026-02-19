// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.utils

import com.intellij.model.Pointer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.UserDataHolder
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.impl.SearchMap
import com.intellij.polySymbols.query.PolySymbolCodeCompletionQueryParams
import com.intellij.polySymbols.query.PolySymbolListSymbolsQueryParams
import com.intellij.polySymbols.query.PolySymbolNameMatchQueryParams
import com.intellij.polySymbols.query.PolySymbolNamesProvider
import com.intellij.polySymbols.query.PolySymbolQueryExecutor
import com.intellij.polySymbols.query.PolySymbolQueryParams
import com.intellij.polySymbols.query.PolySymbolQueryStack
import com.intellij.polySymbols.query.PolySymbolScope
import com.intellij.polySymbols.query.PolySymbolThreadLocalCacheKeyProvider
import com.intellij.polySymbols.query.PolySymbolWithPattern
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.containers.ContainerUtil
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * Used when implementing a [PolySymbolScope], which contains many elements.
 *
 * Caches the list of symbols and uses efficient cache to speed up queries. When extending the class,
 * you only need to override the initialize method and provide parameters to the super constructor to specify how results should be cached.
 */
abstract class PolySymbolScopeWithCache<T : UserDataHolder, K>(
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
) : PolySymbolScope {

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
  protected abstract fun provides(kind: PolySymbolKind): Boolean

  abstract override fun createPointer(): Pointer<out PolySymbolScopeWithCache<T, K>>

  protected open val requiresResolve: Boolean get() = true

  /**
   * This property should be used if it is cheaper to search for a symbol without building the whole cache.
   * One of use cases is when the scope directly maps to indices. When true, it requires that [getMatchingSymbols]
   * with `nameVariant` parameter is implemented.
   */
  protected open val supportsSymbolsMatchingWithoutFullCacheInitialization: Boolean get() = false

  protected open fun getMatchingSymbols(
    kind: PolySymbolKind,
    nameVariant: String,
    cacheDependencies: MutableSet<Any>,
  ): List<PolySymbol> =
    throw NotImplementedError("Subclasses must implement getMatchingSymbols with single name variant if supportsSymbolsMatchingWithoutFullCacheInitialization property returns true")


  override fun getModificationCount(): Long =
    PsiModificationTracker.getInstance(project).modificationCount

  override fun equals(other: Any?): Boolean =
    other === this
    || (other != null
        && other is PolySymbolScopeWithCache<*, *>
        && other::class.java == this::class.java
        && other.key == key
        && other.project == project
        && other.dataHolder == dataHolder)

  override fun hashCode(): Int {
    var result = 31
    result = 31 * result + project.hashCode()
    result = 31 * result + dataHolder.hashCode()
    result = 31 * result + key.hashCode()
    return result
  }

  private fun getNamesProviderToMapCache(): NamesProviderToMapCache {
    val manager = CachedValuesManager.getManager(project)
    return if (key == Unit) {
      manager.getCachedValue(dataHolder, manager.getKeyForClass(this::class.java), {
        CachedValueProvider.Result(NamesProviderToMapCache(project), PsiModificationTracker.NEVER_CHANGED)
      }, false)
    }
    else {
      manager.getCachedValue(dataHolder, manager.getKeyForClass(this::class.java), {
        CachedValueProvider.Result(ConcurrentHashMap<K, NamesProviderToMapCache>(), ModificationTracker.NEVER_CHANGED)
      }, false).getOrPut(key) { NamesProviderToMapCache(project) }
    }
  }

  private fun createCachedSearchMap(namesProvider: PolySymbolNamesProvider): CachedValue<PolySymbolSearchMap> =
    CachedValuesManager.getManager(project).createCachedValue {
      val dependencies = mutableSetOf<Any>()
      val map = PolySymbolSearchMap(namesProvider)
      val unitTestMode = ApplicationManager.getApplication().isUnitTestMode
      initialize(
        {
          if (!provides(it.kind))
            throw IllegalArgumentException("Poly Symbol with unsupported kind: ${it.kind} added. $it (${it.javaClass}")
          if (unitTestMode) {
            val dereferenced = it.createPointer().dereference()
            when {
                dereferenced == null ->
                  throw IllegalArgumentException("Poly Symbol dereferenced from pointer is null. $it (${it.javaClass})")
                !dereferenced.isEquivalentTo(it) ->
                  throw IllegalArgumentException("Poly Symbol dereferenced from pointer is not equivalent to the original. $dereferenced (${dereferenced.javaClass}) !isEquivalentTo $it (${it.javaClass})")
            }
          }
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
    params: PolySymbolNameMatchQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbol> =
    if ((params.queryExecutor.allowResolve || !requiresResolve)
        && provides(qualifiedName.kind)) {
      if (supportsSymbolsMatchingWithoutFullCacheInitialization)
        tryGetMap(params.queryExecutor)?.getMatchingSymbols(qualifiedName, params, stack.copy())?.toList()
        ?: getMatchingSymbolsWithoutFullCacheInit(qualifiedName, params)
      else
        getMap(params.queryExecutor).getMatchingSymbols(qualifiedName, params, stack.copy()).toList()
    }
    else emptyList()

  override fun getSymbols(
    kind: PolySymbolKind,
    params: PolySymbolListSymbolsQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbol> =
    if ((params.queryExecutor.allowResolve || !requiresResolve)
        && provides(kind)) {
      getMap(params.queryExecutor).getSymbols(kind, params).toList()
    }
    else emptyList()

  override fun getCodeCompletions(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolCodeCompletionQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbolCodeCompletionItem> =
    if ((params.queryExecutor.allowResolve || !requiresResolve)
        && provides(qualifiedName.kind)) {
      getMap(params.queryExecutor).getCodeCompletions(qualifiedName, params, stack.copy()).toList()
    }
    else emptyList()

  private fun getMap(queryExecutor: PolySymbolQueryExecutor): PolySymbolSearchMap =
    getNamesProviderToMapCache().getOrCreateMap(queryExecutor.namesProvider, this::createCachedSearchMap)

  private fun tryGetMap(queryExecutor: PolySymbolQueryExecutor): PolySymbolSearchMap? =
    getNamesProviderToMapCache().getMap(queryExecutor.namesProvider)

  private fun getMatchingSymbolsWithoutFullCacheInit(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolNameMatchQueryParams,
  ): List<PolySymbol> {
    val manager = CachedValuesManager.getManager(project)
    val nameCache = manager.getCachedValue(dataHolder) {
      CachedValueProvider.Result(ConcurrentHashMap<String, CachedValue<List<PolySymbol>>>(), PsiModificationTracker.NEVER_CHANGED)
    }
    return params.queryExecutor.namesProvider.getNames(qualifiedName, PolySymbolNamesProvider.Target.NAMES_QUERY).flatMap { name ->
      nameCache.computeIfAbsent(name) { name ->
        manager.createCachedValue {
          val dependencies = mutableSetOf<Any>()
          val symbols = getMatchingSymbols(qualifiedName.kind, name, dependencies)
          symbols.forEach {
            if (!provides(it.kind))
              throw IllegalArgumentException("Poly Symbol with unsupported kind: ${it.kind} provided. $it")
          }
          if (dependencies.isEmpty()) {
            throw IllegalArgumentException(
              "CacheDependencies cannot be empty. Failed to initialize $javaClass. Add ModificationTracker.NEVER_CHANGED if cache should never be dropped.")
          }
          CachedValueProvider.Result.create(symbols, dependencies)
        }
      }.value
    }
  }

  private class NamesProviderToMapCache(private val project: Project) {
    private val cache: ConcurrentMap<List<Any?>, CachedValue<PolySymbolSearchMap>> = ContainerUtil.createConcurrentSoftKeySoftValueMap()
    private var cacheMisses = 0

    fun getOrCreateMap(
      namesProvider: PolySymbolNamesProvider,
      createCachedSearchMap: (namesProvider: PolySymbolNamesProvider) -> CachedValue<PolySymbolSearchMap>,
    ): PolySymbolSearchMap {
      if (cacheMisses > 20) {
        // Get rid of old soft keys
        cacheMisses = 0
        cache.clear()
      }
      return cache.getOrPut(PolySymbolThreadLocalCacheKeyProvider.getCacheKeys(namesProvider, project)) {
        cacheMisses++
        createCachedSearchMap(namesProvider)
      }.value
    }

    fun getMap(
      namesProvider: PolySymbolNamesProvider,
    ): PolySymbolSearchMap? =
      cache[PolySymbolThreadLocalCacheKeyProvider.getCacheKeys(namesProvider, project)]?.value
  }

  private class PolySymbolSearchMap(namesProvider: PolySymbolNamesProvider) :
    SearchMap<PolySymbol>(namesProvider) {

    override fun Sequence<PolySymbol>.mapAndFilter(params: PolySymbolQueryParams): Sequence<PolySymbol> = this

    fun add(symbol: PolySymbol) {
      add(symbol.qualifiedName, (symbol as? PolySymbolWithPattern)?.pattern, symbol)
    }

  }

}