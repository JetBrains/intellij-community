// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query.impl

import com.intellij.model.Pointer
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.RecursionManager
import com.intellij.util.applyIf
import com.intellij.util.asSafely
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolQualifiedKind
import com.intellij.webSymbols.WebSymbolQualifiedName
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.context.WebSymbolsContext
import com.intellij.webSymbols.impl.filterByQueryParams
import com.intellij.webSymbols.impl.selectBest
import com.intellij.webSymbols.query.*
import com.intellij.webSymbols.utils.*
import org.jetbrains.annotations.ApiStatus
import java.util.*
import kotlin.math.max
import kotlin.math.min

@ApiStatus.Internal
class WebSymbolsQueryExecutorImpl(
  rootScope: List<WebSymbolsScope>,
  override val namesProvider: WebSymbolNamesProvider,
  override val resultsCustomizer: WebSymbolsQueryResultsCustomizer,
  override val context: WebSymbolsContext,
  override val allowResolve: Boolean,
) : WebSymbolsQueryExecutor {

  private val rootScope: List<WebSymbolsScope> = initializeCompoundScopes(rootScope)

  override fun hashCode(): Int =
    Objects.hash(rootScope, context, namesProvider, resultsCustomizer)

  override fun equals(other: Any?): Boolean =
    other === this ||
    other is WebSymbolsQueryExecutorImpl
    && other.context == context
    && other.rootScope == rootScope
    && other.namesProvider == namesProvider
    && other.resultsCustomizer == resultsCustomizer

  override fun createPointer(): Pointer<WebSymbolsQueryExecutor> {
    val namesProviderPtr = this.namesProvider.createPointer()
    val context = this.context
    val allowResolve = this.allowResolve
    val scopePtr = this.resultsCustomizer.createPointer()
    val rootScopePointers = this.rootScope.map { it.createPointer() }
    return Pointer<WebSymbolsQueryExecutor> {
      @Suppress("UNCHECKED_CAST")
      val rootScope = rootScopePointers.map { it.dereference() }
                        .takeIf { it.all { c -> c != null } } as? List<WebSymbolsScope>
                      ?: return@Pointer null

      val namesProvider = namesProviderPtr.dereference()
                          ?: return@Pointer null

      val scope = scopePtr.dereference()
                  ?: return@Pointer null
      WebSymbolsQueryExecutorImpl(rootScope, namesProvider, scope, context, allowResolve)
    }
  }

  override fun runNameMatchQuery(
    path: List<WebSymbolQualifiedName>,
    virtualSymbols: Boolean,
    abstractSymbols: Boolean,
    strictScope: Boolean,
    additionalScope: List<WebSymbolsScope>,
  ): List<WebSymbol> =
    runNameMatchQuery(path, WebSymbolsNameMatchQueryParams.create(this, virtualSymbols, abstractSymbols, strictScope), additionalScope)

  override fun runListSymbolsQuery(
    path: List<WebSymbolQualifiedName>,
    qualifiedKind: WebSymbolQualifiedKind,
    expandPatterns: Boolean,
    virtualSymbols: Boolean,
    abstractSymbols: Boolean,
    strictScope: Boolean,
    additionalScope: List<WebSymbolsScope>,
  ): List<WebSymbol> =
    runListSymbolsQuery(path + qualifiedKind.withName(""),
                        WebSymbolsListSymbolsQueryParams.create(this, expandPatterns = expandPatterns, virtualSymbols = virtualSymbols,
                                                                abstractSymbols = abstractSymbols, strictScope = strictScope), additionalScope)

  override fun runCodeCompletionQuery(
    path: List<WebSymbolQualifiedName>,
    position: Int,
    virtualSymbols: Boolean,
    additionalScope: List<WebSymbolsScope>,
  ): List<WebSymbolCodeCompletionItem> =
    runCodeCompletionQuery(path, WebSymbolsCodeCompletionQueryParams.create(this, position, virtualSymbols), additionalScope)

  override fun withNameConversionRules(rules: List<WebSymbolNameConversionRules>): WebSymbolsQueryExecutor =
    if (rules.isEmpty())
      this
    else
      WebSymbolsQueryExecutorImpl(rootScope, namesProvider.withRules(rules), resultsCustomizer, context, allowResolve)

  override fun hasExclusiveScopeFor(qualifiedKind: WebSymbolQualifiedKind, scope: List<WebSymbolsScope>): Boolean {
    return buildQueryScope(scope).any { it.isExclusiveFor(qualifiedKind) }
  }

  private fun initializeCompoundScopes(rootScope: List<WebSymbolsScope>): List<WebSymbolsScope> {
    if (rootScope.any { it is WebSymbolsCompoundScope }) {
      val compoundScopeQueryExecutor = WebSymbolsQueryExecutorImpl(
        rootScope.filter { it !is WebSymbolsCompoundScope },
        namesProvider, resultsCustomizer, context, allowResolve
      )
      return rootScope.flatMap {
        if (it is WebSymbolsCompoundScope) {
          it.getScopes(compoundScopeQueryExecutor)
        }
        else {
          listOf(it)
        }
      }
    }
    else return rootScope
  }

  private fun buildQueryScope(additionalScope: List<WebSymbolsScope>): MutableSet<WebSymbolsScope> {
    val finalScope = rootScope.toMutableSet()
    additionalScope.flatMapTo(finalScope) {
      when (it) {
        is WebSymbolsCompoundScope -> it.getScopes(this)
        is WebSymbol -> it.queryScope
        else -> listOf(it)
      }
    }
    return finalScope
  }

  private fun runNameMatchQuery(
    path: List<WebSymbolQualifiedName>,
    queryParams: WebSymbolsNameMatchQueryParams,
    additionalScope: List<WebSymbolsScope>,
  ): List<WebSymbol> =
    runQuery(path, queryParams, additionalScope) {
      finalContext: Collection<WebSymbolsScope>,
      qualifiedName: WebSymbolQualifiedName,
      params: WebSymbolsNameMatchQueryParams,
      ->
      val result = finalContext
        .takeLastUntilExclusiveScopeFor(qualifiedName.qualifiedKind)
        .asSequence()
        .flatMap { scope ->
          ProgressManager.checkCanceled()
          scope.getMatchingSymbols(qualifiedName, params, Stack(finalContext))
        }
        .filter { it !is WebSymbolMatch || it.nameSegments.size > 1 || (it.nameSegments.isNotEmpty() && it.nameSegments[0].problem == null) }
        .distinct()
        .toList()
        .customizeMatches(params.strictScope, qualifiedName)
        .selectBest(WebSymbol::nameSegments, WebSymbol::priority, WebSymbol::extension)
      result
    }

  private fun runListSymbolsQuery(
    path: List<WebSymbolQualifiedName>, queryParams: WebSymbolsListSymbolsQueryParams,
    additionalScope: List<WebSymbolsScope>,
  ): List<WebSymbol> =
    runQuery(path, queryParams, additionalScope) {
      finalContext: Collection<WebSymbolsScope>,
      qualifiedName: WebSymbolQualifiedName,
      params: WebSymbolsListSymbolsQueryParams,
      ->
      val result = finalContext
        .takeLastUntilExclusiveScopeFor(qualifiedName.qualifiedKind)
        .asSequence()
        .flatMap { scope ->
          ProgressManager.checkCanceled()
          scope.getSymbols(qualifiedName.qualifiedKind, params, Stack(finalContext))
        }
        .filterIsInstance<WebSymbol>()
        .distinct()
        .filterByQueryParams(params)
        .applyIf(params.expandPatterns) {
          flatMap {
            if (it.pattern != null)
              it.expandPattern(Stack(finalContext), params)
            else
              listOf(it)
          }
        }
        .groupBy {
          queryParams.queryExecutor.namesProvider
            .getNames(qualifiedName.copy(name = it.name), WebSymbolNamesProvider.Target.NAMES_MAP_STORAGE).firstOrNull()
          ?: it.name
        }
        .flatMap { (name, list) ->
          ProgressManager.checkCanceled()
          list
            .customizeMatches(params.strictScope, qualifiedName.copy(name = name))
            .selectBest(WebSymbol::nameSegments, WebSymbol::priority, WebSymbol::extension)
            .applyIf(params.expandPatterns) {
              asSingleSymbol()
                ?.let { symbol ->
                  params.queryExecutor.namesProvider
                    .getNames(symbol.qualifiedName, WebSymbolNamesProvider.Target.CODE_COMPLETION_VARIANTS)
                    .firstOrNull()
                    ?.let { name -> listOf(symbol.withMatchedName(name)) }
                }
              ?: emptyList()
            }
        }
      result
    }

  private fun runCodeCompletionQuery(
    path: List<WebSymbolQualifiedName>, queryParams: WebSymbolsCodeCompletionQueryParams,
    additionalScope: List<WebSymbolsScope>,
  ): List<WebSymbolCodeCompletionItem> =
    runQuery(path, queryParams, additionalScope) {
      finalContext: Collection<WebSymbolsScope>,
      pathSection: WebSymbolQualifiedName,
      params: WebSymbolsCodeCompletionQueryParams,
      ->
      var proximityBase = 0
      var nextProximityBase = 0
      var previousName: String? = null
      val pos = params.position
      val result = finalContext
        .takeLastUntilExclusiveScopeFor(pathSection.qualifiedKind)
        .asSequence()
        .flatMap { scope ->
          if (scope !is WebSymbol || !scope.extension || scope.name != previousName) {
            previousName = (scope as? WebSymbol)?.name
            proximityBase = nextProximityBase
          }
          ProgressManager.checkCanceled()
          scope.getCodeCompletions(pathSection, params, Stack(finalContext))
            .mapNotNull { item ->
              if (item.offset > pos || item.symbol?.hideFromCompletion == true)
                return@mapNotNull null
              val newProximity = (item.proximity ?: 0) + proximityBase
              if (newProximity + 1 > nextProximityBase) {
                // Increase proximity base for next scope, but allow to exceed it
                nextProximityBase = min(proximityBase + 5, newProximity) + 1
              }
              item.withProximity(newProximity)
            }
        }
        .mapWithSymbolPriority()
        .mapNotNull {
          ProgressManager.checkCanceled()
          this.resultsCustomizer.apply(it, pathSection.qualifiedKind)
        }
        .toList()
        .sortAndDeduplicate()
      result
    }


  override fun getModificationCount(): Long =
    rootScope.sumOf { it.modificationCount } + namesProvider.modificationCount + resultsCustomizer.modificationCount

  @RequiresReadLock
  private fun <T, P : WebSymbolsQueryParams> runQuery(
    path: List<WebSymbolQualifiedName>,
    params: P,
    additionalScope: List<WebSymbolsScope>,
    finalProcessor: (
      context: Collection<WebSymbolsScope>,
      pathSection: WebSymbolQualifiedName,
      params: P,
    ) -> List<T>,
  ): List<T> {
    ProgressManager.checkCanceled()
    if (path.isEmpty()) return emptyList()

    val scope = buildQueryScope(additionalScope)
    return RecursionManager.doPreventingRecursion(Pair(path, params.virtualSymbols), false) {
      val contextQueryParams = WebSymbolsNameMatchQueryParams.create(this, true, false)
      var i = 0
      while (i < path.size - 1) {
        val qName = path[i++]
        if (qName.name.isEmpty()) return@doPreventingRecursion emptyList()
        val scopeSymbols = scope
          .takeLastUntilExclusiveScopeFor(qName.qualifiedKind)
          .flatMap {
            it.getMatchingSymbols(qName, contextQueryParams, Stack(scope))
          }
        scopeSymbols.flatMapTo(scope) {
          it.queryScope
        }
      }
      val lastSection = path.last()
      finalProcessor(scope, lastSection, params)
    } ?: run {
      thisLogger().warn("Recursive Web Symbols query: ${path.joinToString("/")} with virtualSymbols=${params.virtualSymbols}.\n" +
                        "Root scope: " + rootScope.map {
        it.asSafely<WebSymbol>()?.let { symbol -> symbol.kind + "/" + symbol.name } ?: it
      } + "\n" +
                        "Additional scope: " + additionalScope.map {
        it.asSafely<WebSymbol>()?.let { symbol -> symbol.kind + "/" + symbol.name } ?: it
      })
      emptyList()
    }
  }

  private fun Collection<WebSymbolsScope>.takeLastUntilExclusiveScopeFor(qualifiedKind: WebSymbolQualifiedKind): List<WebSymbolsScope> =
    toList()
      .let { list ->
        list.subList(max(0, list.indexOfLast { it.isExclusiveFor(qualifiedKind) }), list.size)
      }

  private fun List<WebSymbolCodeCompletionItem>.sortAndDeduplicate(): List<WebSymbolCodeCompletionItem> =
    groupBy { Triple(it.name, it.displayName, it.offset) }
      .mapNotNull { (_, items) ->
        if (items.size == 1) {
          items[0]
        }
        else {
          items
            .sortedWith(Comparator.comparing { it: WebSymbolCodeCompletionItem -> -(it.priority ?: WebSymbol.Priority.NORMAL).ordinal }
                          .thenComparingInt { -(it.proximity ?: 0) })
            .firstOrNull()
        }
      }

  private fun Sequence<WebSymbolCodeCompletionItem>.mapWithSymbolPriority() =
    map { item ->
      item.symbol
        ?.priority
        ?.takeIf { it > (item.priority ?: WebSymbol.Priority.LOWEST) }
        ?.let { item.withPriority(it) }
      ?: item
    }

  private fun WebSymbol.expandPattern(
    context: Stack<WebSymbolsScope>,
    params: WebSymbolsListSymbolsQueryParams,
  ): List<WebSymbol> =
    pattern?.let { pattern ->
      context.push(this)
      try {
        return pattern
          .list(this, context, params)
          .map {
            WebSymbolMatch.create(it.name, it.segments, namespace, kind, origin)
          }
      }
      finally {
        context.pop()
      }
    } ?: listOf(this)

  private fun List<WebSymbol>.customizeMatches(strict: Boolean, qualifiedName: WebSymbolQualifiedName): List<WebSymbol> =
    if (isEmpty())
      this
    else {
      ProgressManager.checkCanceled()
      resultsCustomizer.apply(this, strict, qualifiedName)
    }

}

