// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(IntellijInternalApi::class)

package com.intellij.polySymbols.utils

import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.RecursionManager
import com.intellij.polySymbols.*
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.query.*
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.applyIf

abstract class PolySymbolIsolatedMappingScope<T : PsiElement>(
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
) : PolySymbolScope {

  protected abstract fun acceptSymbol(symbol: PolySymbol): Boolean

  protected abstract val subScopeBuilder: (PolySymbolQueryExecutor, T) -> List<PolySymbolScope>

  override fun getCodeCompletions(qualifiedName: PolySymbolQualifiedName, params: PolySymbolCodeCompletionQueryParams, stack: PolySymbolQueryStack): List<PolySymbolCodeCompletionItem> {
    if (!params.queryExecutor.allowResolve || (framework != null && params.framework != framework))
      return emptyList()
    val sourceKind = mappings[qualifiedName.qualifiedKind] ?: return emptyList()
    var result: List<PolySymbolCodeCompletionItem> = emptyList()
    RecursionManager.runInNewContext {
      result = subQuery.codeCompletionQuery(sourceKind, qualifiedName.name, params.position) {
        copyFiltersFrom(params)
        additionalScope(additionalScope)
      }
        .filter { it.symbol?.let { acceptSymbol(it) } != false }
    }
    return result
  }

  override fun getMatchingSymbols(qualifiedName: PolySymbolQualifiedName, params: PolySymbolNameMatchQueryParams, stack: PolySymbolQueryStack): List<PolySymbol> {
    if (!params.queryExecutor.allowResolve || (framework != null && params.framework != framework))
      return emptyList()
    val sourceKind = mappings[qualifiedName.qualifiedKind] ?: return emptyList()
    var result: List<PolySymbol> = emptyList()
    RecursionManager.runInNewContext {
      result = subQuery.nameMatchQuery(sourceKind, qualifiedName.name) {
        copyFiltersFrom(params)
        strictScope(params.strictScope)
        additionalScope(additionalScope)
      }
        .filter { acceptSymbol(it) }
        .map { it.withMatchedKind(qualifiedName.qualifiedKind) }
    }
    return result
  }

  override fun getSymbols(qualifiedKind: PolySymbolQualifiedKind, params: PolySymbolListSymbolsQueryParams, stack: PolySymbolQueryStack): List<PolySymbol> {
    if (!params.queryExecutor.allowResolve || (framework != null && params.framework != framework))
      return emptyList()
    val sourceKind = mappings[qualifiedKind] ?: return emptyList()
    var result: List<PolySymbol> = emptyList()
    RecursionManager.runInNewContext {
      result = subQuery.listSymbolsQuery(sourceKind, params.expandPatterns) {
        copyFiltersFrom(params)
        strictScope(params.strictScope)
        additionalScope(additionalScope)
      }
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
        && other is PolySymbolIsolatedMappingScope<*>
        && other::class.java == this::class.java
        && other.framework == framework
        && other.location == location)

  override fun hashCode(): Int =
    31 * framework.hashCode() + location.hashCode()

  private val subQuery by lazy(LazyThreadSafetyMode.PUBLICATION) {
    getCachedSubQueryExecutorAndScope().first
  }
  private val additionalScope by lazy(LazyThreadSafetyMode.PUBLICATION) {
    getCachedSubQueryExecutorAndScope().second
  }

  private fun getCachedSubQueryExecutorAndScope(): Pair<PolySymbolQueryExecutor, List<PolySymbolScope>> {
    val location = this@PolySymbolIsolatedMappingScope.location
    val builder = subScopeBuilder
    val manager = CachedValuesManager.getManager(location.project)
    val cachedValueKey = manager.getKeyForClass<Pair<PolySymbolQueryExecutor, List<PolySymbolScope>>>(builder.javaClass)
    return manager.getCachedValue(location, cachedValueKey, {
      val executor = PolySymbolQueryExecutorFactory.create(location)
      val scope = builder(executor, location)
      CachedValueProvider.Result.create(Pair(executor, scope.toList()), PsiModificationTracker.MODIFICATION_COUNT)
    }, false)
  }

}