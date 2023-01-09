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
import com.intellij.webSymbols.query.impl.SearchMap
import com.intellij.webSymbols.query.*
import com.intellij.webSymbols.utils.psiModificationCount
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

abstract class WebSymbolsScopeWithCache<T : UserDataHolder, K>(protected val framework: FrameworkId?,
                                                               protected val project: Project,
                                                               protected val dataHolder: T,
                                                               protected val key: K) : WebSymbolsScope {

  abstract override fun createPointer(): Pointer<out WebSymbolsScopeWithCache<T, K>>

  private val requiresResolve: Boolean get() = true

  override fun getModificationCount(): Long =
    project.psiModificationCount

  final override fun equals(other: Any?): Boolean =
    other === this
    || (other != null
        && other is WebSymbolsScopeWithCache<*, *>
        && other::class.java == this::class.java
        && other.framework == framework
        && other.key == key
        && other.project == project
        && other.dataHolder == dataHolder)

  final override fun hashCode(): Int =
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

  protected abstract fun initialize(consumer: (WebSymbol) -> Unit, cacheDependencies: MutableSet<Any>)

  private fun createCachedSearchMap(namesProvider: WebSymbolNamesProvider): CachedValue<WebSymbolsSearchMap> =
    CachedValuesManager.getManager(project).createCachedValue {
      val dependencies = mutableSetOf<Any>()
      val map = WebSymbolsSearchMap(namesProvider, framework)
      initialize(map::add, dependencies)
      if (dependencies.isEmpty()) {
        throw IllegalArgumentException(
          "CacheDependencies cannot be empty. Add ModificationTracker.NEVER_CHANGED if cache should never be dropped.")
      }
      dependencies.add(namesProvider)
      CachedValueProvider.Result.create(map, dependencies.toList())
    }

  protected open fun provides(namespace: SymbolNamespace, kind: SymbolKind): Boolean = true

  override fun getSymbols(namespace: SymbolNamespace,
                          kind: SymbolKind,
                          name: String?,
                          params: WebSymbolsNameMatchQueryParams,
                          scope: Stack<WebSymbolsScope>): List<WebSymbolsScope> =
    if (namespace != null
        && (params.queryExecutor.allowResolve || !requiresResolve)
        && (framework == null || params.framework == framework)
        && provides(namespace, kind)) {
      getMap(params.queryExecutor).getSymbols(namespace, kind, name, params, Stack(scope)).toList()
    }
    else emptyList()

  override fun getCodeCompletions(namespace: SymbolNamespace,
                                  kind: SymbolKind,
                                  name: String?,
                                  params: WebSymbolsCodeCompletionQueryParams,
                                  scope: Stack<WebSymbolsScope>): List<WebSymbolCodeCompletionItem> =
    if (namespace != null
        && (params.queryExecutor.allowResolve || !requiresResolve)
        && (framework == null || params.framework == framework)
        && provides(namespace, kind)) {
      getMap(params.queryExecutor).getCodeCompletions(namespace, kind, name, params, Stack(scope)).toList()
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
    : SearchMap<WebSymbol>(namesProvider) {

    override fun Sequence<WebSymbol>.mapAndFilter(params: WebSymbolsQueryParams): Sequence<WebSymbol> = this

    fun add(symbol: WebSymbol) {
      assert(symbol.origin.framework == framework) {
        "WebSymbolsScope only accepts symbols with framework: $framework, but symbol with framework ${symbol.origin.framework} was added."
      }
      add(symbol.namespace, symbol.kind, symbol.name, symbol.pattern, symbol)
    }

  }

}