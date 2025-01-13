package com.intellij.webSymbols.utils

import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.applyIf
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolQualifiedKind
import com.intellij.webSymbols.WebSymbolQualifiedName
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.query.*
import com.intellij.webSymbols.utils.withMatchedKind

abstract class WebSymbolsIsolatedMappingScope<T : PsiElement>(
  protected val mappings: Map<WebSymbolQualifiedKind, WebSymbolQualifiedKind>,
  protected val location: T,
) : WebSymbolsScope {

  protected abstract fun acceptSymbol(symbol: WebSymbol): Boolean

  protected abstract val subScopeBuilder: (WebSymbolsQueryExecutor, T) -> List<WebSymbolsScope>

  final override fun getCodeCompletions(qualifiedName: WebSymbolQualifiedName, params: WebSymbolsCodeCompletionQueryParams, scope: Stack<WebSymbolsScope>): List<WebSymbolCodeCompletionItem> {
    val sourceKind = mappings[qualifiedName.qualifiedKind] ?: return emptyList()
    var result: List<WebSymbolCodeCompletionItem> = emptyList()
    RecursionManager.runInNewContext {
      result = subQuery.runCodeCompletionQuery(sourceKind, qualifiedName.name, params.position, params.virtualSymbols, additionalScope)
        .filter { it.symbol?.let { acceptSymbol(it) } != false }
    }
    return result
  }

  final override fun getMatchingSymbols(qualifiedName: WebSymbolQualifiedName, params: WebSymbolsNameMatchQueryParams, scope: Stack<WebSymbolsScope>): List<WebSymbol> {
    val sourceKind = mappings[qualifiedName.qualifiedKind] ?: return emptyList()
    var result: List<WebSymbol> = emptyList()
    RecursionManager.runInNewContext {
      result = subQuery.runNameMatchQuery(sourceKind.withName(qualifiedName.name), params.virtualSymbols, params.abstractSymbols, params.strictScope, additionalScope)
        .filter { acceptSymbol(it) }
        .map { it.withMatchedKind(qualifiedName.qualifiedKind) }
    }
    return result
  }

  final override fun getSymbols(qualifiedKind: WebSymbolQualifiedKind, params: WebSymbolsListSymbolsQueryParams, scope: Stack<WebSymbolsScope>): List<WebSymbolsScope> {
    val sourceKind = mappings[qualifiedKind] ?: return emptyList()
    var result: List<WebSymbol> = emptyList()
    RecursionManager.runInNewContext {
      result = subQuery.runListSymbolsQuery(sourceKind, params.expandPatterns, params.virtualSymbols, params.abstractSymbols, params.strictScope, additionalScope)
        .filter { acceptSymbol(it) }
        .applyIf(params.expandPatterns) { map { it.withMatchedKind(qualifiedKind) } }
    }
    return result
  }

  final override fun getModificationCount(): Long = 0

  override fun equals(other: Any?): Boolean =
    other === this
    || (other is WebSymbolsIsolatedMappingScope<*> && other.javaClass == this.javaClass && location == other.location)

  override fun hashCode(): Int =
    location.hashCode()

  private val subQuery by lazy(LazyThreadSafetyMode.PUBLICATION) {
    getCachedSubQueryExecutorAndScope().first
  }
  private val additionalScope by lazy(LazyThreadSafetyMode.PUBLICATION) {
    getCachedSubQueryExecutorAndScope().second
  }

  private fun getCachedSubQueryExecutorAndScope(): Pair<WebSymbolsQueryExecutor, List<WebSymbolsScope>> {
    val location = this@WebSymbolsIsolatedMappingScope.location
    val builder = subScopeBuilder
    val manager = CachedValuesManager.getManager(location.project)
    val key = manager.getKeyForClass<Pair<WebSymbolsQueryExecutor, List<WebSymbolsScope>>>(builder.javaClass)
    return manager.getCachedValue(location, key, {
      val executor = WebSymbolsQueryExecutorFactory.create(location)
      val scope = builder(executor, location)
      CachedValueProvider.Result.create(Pair(executor, scope.toList()), PsiModificationTracker.MODIFICATION_COUNT)
    }, false)
  }

}