// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.webSymbols.impl.SearchMap
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
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

abstract class WebSymbolsContainerWithCache<T : UserDataHolder, K>(protected val framework: FrameworkId?,
                                                                   protected val project: Project,
                                                                   protected val dataHolder: T,
                                                                   protected val key: K) : WebSymbolsContainer {

  abstract override fun createPointer(): Pointer<out WebSymbolsContainerWithCache<T, K>>

  private val requiresResolve: Boolean get() = true

  override fun getModificationCount(): Long =
    project.psiModificationCount

  final override fun equals(other: Any?): Boolean =
    other === this
    || (other != null
        && other is WebSymbolsContainerWithCache<*, *>
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

  protected open fun provides(namespace: WebSymbolsContainer.Namespace, kind: String): Boolean = true

  override fun getSymbols(namespace: WebSymbolsContainer.Namespace?,
                          kind: String,
                          name: String?,
                          params: WebSymbolsNameMatchQueryParams,
                          context: Stack<WebSymbolsContainer>): List<WebSymbolsContainer> =
    if (namespace != null
        && (params.registry.allowResolve || !requiresResolve)
        && (framework == null || params.framework == framework)
        && provides(namespace, kind)) {
      getMap(params.registry).getSymbols(namespace, kind, name, params, Stack(context)).toList()
    }
    else emptyList()

  override fun getCodeCompletions(namespace: WebSymbolsContainer.Namespace?,
                                  kind: String,
                                  name: String?,
                                  params: WebSymbolsCodeCompletionQueryParams,
                                  context: Stack<WebSymbolsContainer>): List<WebSymbolCodeCompletionItem> =
    if (namespace != null
        && (params.registry.allowResolve || !requiresResolve)
        && (framework == null || params.framework == framework)
        && provides(namespace, kind)) {
      getMap(params.registry).getCodeCompletions(namespace, kind, name, params, Stack(context)).toList()
    }
    else emptyList()

  private fun getMap(registry: WebSymbolsRegistry): WebSymbolsSearchMap =
    getNamesProviderToMapCache().getOrCreateMap(registry.namesProvider, this::createCachedSearchMap)

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

    override fun Sequence<WebSymbol>.mapAndFilter(params: WebSymbolsRegistryQueryParams): Sequence<WebSymbol> = this

    fun add(symbol: WebSymbol) {
      assert(symbol.origin.framework == framework) {
        "WebSymbolsContainer only accepts symbols with framework: $framework, but symbol with "
      }
      add(symbol.namespace, symbol.kind, symbol.name, symbol.pattern, symbol)
    }

  }

}