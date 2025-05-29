// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(IntellijInternalApi::class)

package com.intellij.polySymbols.utils

import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.applyIf
import com.intellij.util.containers.Stack
import com.intellij.polySymbols.*
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.query.*
import java.util.*

abstract class PolySymbolsIsolatedMappingScope<T : PsiElement>(
  protected val mappings: Map<PolySymbolQualifiedKind, PolySymbolQualifiedKind>,
  /**
   * Allows to optimize for symbols with a particular [PolySymbolOrigin.framework].
   * If `null` all symbols will be accepted and scope will be queried in all contexts.
   */
  protected val framework: FrameworkId?,
  /**
   * Location for which the isolated query executor should be built.
   */
  protected val location: T,
) : PolySymbolsScope {

  protected abstract fun acceptSymbol(symbol: PolySymbol): Boolean

  protected abstract val subScopeBuilder: (PolySymbolsQueryExecutor, T) -> List<PolySymbolsScope>

  override fun getCodeCompletions(qualifiedName: PolySymbolQualifiedName, params: WebSymbolsCodeCompletionQueryParams, scope: Stack<PolySymbolsScope>): List<PolySymbolCodeCompletionItem> {
    if (!params.queryExecutor.allowResolve || (framework != null && params.framework != framework))
      return emptyList()
    val sourceKind = mappings[qualifiedName.qualifiedKind] ?: return emptyList()
    var result: List<PolySymbolCodeCompletionItem> = emptyList()
    RecursionManager.runInNewContext {
      result = subQuery.runCodeCompletionQuery(sourceKind, qualifiedName.name, params.position, params.virtualSymbols, additionalScope)
        .filter { it.symbol?.let { acceptSymbol(it) } != false }
    }
    return result
  }

  override fun getMatchingSymbols(qualifiedName: PolySymbolQualifiedName, params: WebSymbolsNameMatchQueryParams, scope: Stack<PolySymbolsScope>): List<PolySymbol> {
    if (!params.queryExecutor.allowResolve || (framework != null && params.framework != framework))
      return emptyList()
    val sourceKind = mappings[qualifiedName.qualifiedKind] ?: return emptyList()
    var result: List<PolySymbol> = emptyList()
    RecursionManager.runInNewContext {
      result = subQuery.runNameMatchQuery(sourceKind.withName(qualifiedName.name), params.virtualSymbols, params.abstractSymbols, params.strictScope, additionalScope)
        .filter { acceptSymbol(it) }
        .map { it.withMatchedKind(qualifiedName.qualifiedKind) }
    }
    return result
  }

  override fun getSymbols(qualifiedKind: PolySymbolQualifiedKind, params: WebSymbolsListSymbolsQueryParams, scope: Stack<PolySymbolsScope>): List<PolySymbolsScope> {
    if (!params.queryExecutor.allowResolve || (framework != null && params.framework != framework))
      return emptyList()
    val sourceKind = mappings[qualifiedKind] ?: return emptyList()
    var result: List<PolySymbol> = emptyList()
    RecursionManager.runInNewContext {
      result = subQuery.runListSymbolsQuery(sourceKind, params.expandPatterns, params.virtualSymbols, params.abstractSymbols, params.strictScope, additionalScope)
        .filter { acceptSymbol(it) }
        .applyIf(params.expandPatterns) { map { it.withMatchedKind(qualifiedKind) } }
    }
    return result
  }

  final override fun getModificationCount(): Long =
    PsiModificationTracker.getInstance(location.project).modificationCount

  final override fun equals(other: Any?): Boolean =
    other === this
    || (other != null
        && other is PolySymbolsIsolatedMappingScope<*>
        && other::class.java == this::class.java
        && other.framework == framework
        && other.location == location)

  override fun hashCode(): Int =
    Objects.hash(framework, location)

  private val subQuery by lazy(LazyThreadSafetyMode.PUBLICATION) {
    getCachedSubQueryExecutorAndScope().first
  }
  private val additionalScope by lazy(LazyThreadSafetyMode.PUBLICATION) {
    getCachedSubQueryExecutorAndScope().second
  }

  private fun getCachedSubQueryExecutorAndScope(): Pair<PolySymbolsQueryExecutor, List<PolySymbolsScope>> {
    val location = this@PolySymbolsIsolatedMappingScope.location
    val builder = subScopeBuilder
    val manager = CachedValuesManager.getManager(location.project)
    val cachedValueKey = manager.getKeyForClass<Pair<PolySymbolsQueryExecutor, List<PolySymbolsScope>>>(builder.javaClass)
    return manager.getCachedValue(location, cachedValueKey, {
      val executor = PolySymbolsQueryExecutorFactory.create(location)
      val scope = builder(executor, location)
      CachedValueProvider.Result.create(Pair(executor, scope.toList()), PsiModificationTracker.MODIFICATION_COUNT)
    }, false)
  }

}