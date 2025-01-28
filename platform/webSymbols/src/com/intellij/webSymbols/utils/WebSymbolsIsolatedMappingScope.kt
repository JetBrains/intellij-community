package com.intellij.webSymbols.utils

import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.applyIf
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.*
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.query.*
import java.util.*

abstract class WebSymbolsIsolatedMappingScope<T : PsiElement>(
  protected val mappings: Map<WebSymbolQualifiedKind, WebSymbolQualifiedKind>,
  /**
   * Allows to optimize for symbols with a particular [WebSymbolOrigin.framework].
   * If `null` all symbols will be accepted and scope will be queried in all contexts.
   */
  protected val framework: FrameworkId?,
  /**
   * Location for which the isolated query executor should be built.
   */
  protected val location: T,
) : WebSymbolsScope {

  protected abstract fun acceptSymbol(symbol: WebSymbol): Boolean

  protected abstract val subScopeBuilder: (WebSymbolsQueryExecutor, T) -> List<WebSymbolsScope>

  override fun getCodeCompletions(qualifiedName: WebSymbolQualifiedName, params: WebSymbolsCodeCompletionQueryParams, scope: Stack<WebSymbolsScope>): List<WebSymbolCodeCompletionItem> {
    if (!params.queryExecutor.allowResolve || (framework != null && params.framework != framework))
      return emptyList()
    val sourceKind = mappings[qualifiedName.qualifiedKind] ?: return emptyList()
    var result: List<WebSymbolCodeCompletionItem> = emptyList()
    RecursionManager.runInNewContext {
      result = subQuery.runCodeCompletionQuery(sourceKind, qualifiedName.name, params.position, params.virtualSymbols, additionalScope)
        .filter { it.symbol?.let { acceptSymbol(it) } != false }
    }
    return result
  }

  override fun getMatchingSymbols(qualifiedName: WebSymbolQualifiedName, params: WebSymbolsNameMatchQueryParams, scope: Stack<WebSymbolsScope>): List<WebSymbol> {
    if (!params.queryExecutor.allowResolve || (framework != null && params.framework != framework))
      return emptyList()
    val sourceKind = mappings[qualifiedName.qualifiedKind] ?: return emptyList()
    var result: List<WebSymbol> = emptyList()
    RecursionManager.runInNewContext {
      result = subQuery.runNameMatchQuery(sourceKind.withName(qualifiedName.name), params.virtualSymbols, params.abstractSymbols, params.strictScope, additionalScope)
        .filter { acceptSymbol(it) }
        .map { it.withMatchedKind(qualifiedName.qualifiedKind) }
    }
    return result
  }

  override fun getSymbols(qualifiedKind: WebSymbolQualifiedKind, params: WebSymbolsListSymbolsQueryParams, scope: Stack<WebSymbolsScope>): List<WebSymbolsScope> {
    if (!params.queryExecutor.allowResolve || (framework != null && params.framework != framework))
      return emptyList()
    val sourceKind = mappings[qualifiedKind] ?: return emptyList()
    var result: List<WebSymbol> = emptyList()
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
        && other is WebSymbolsIsolatedMappingScope<*>
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

  private fun getCachedSubQueryExecutorAndScope(): Pair<WebSymbolsQueryExecutor, List<WebSymbolsScope>> {
    val location = this@WebSymbolsIsolatedMappingScope.location
    val builder = subScopeBuilder
    val manager = CachedValuesManager.getManager(location.project)
    val cachedValueKey = manager.getKeyForClass<Pair<WebSymbolsQueryExecutor, List<WebSymbolsScope>>>(builder.javaClass)
    return manager.getCachedValue(location, cachedValueKey, {
      val executor = WebSymbolsQueryExecutorFactory.create(location)
      val scope = builder(executor, location)
      CachedValueProvider.Result.create(Pair(executor, scope.toList()), PsiModificationTracker.MODIFICATION_COUNT)
    }, false)
  }

}