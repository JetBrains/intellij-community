// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query.impl

import com.intellij.model.Pointer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.RecursionManager
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.*
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.context.WebSymbolsContext
import com.intellij.webSymbols.impl.selectBest
import com.intellij.webSymbols.query.*
import com.intellij.webSymbols.utils.hideFromCompletion
import com.intellij.webSymbols.utils.nameSegments
import java.util.*
import kotlin.math.max
import kotlin.math.min

internal class WebSymbolsQueryExecutorImpl(private val rootScope: List<WebSymbolsScope>,
                                           override val namesProvider: WebSymbolNamesProvider,
                                           override val resultsCustomizer: WebSymbolsQueryResultsCustomizer,
                                           override val context: WebSymbolsContext,
                                           override val allowResolve: Boolean) : WebSymbolsQueryExecutor {

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

  override fun runNameMatchQuery(path: List<WebSymbolQualifiedName>,
                                 virtualSymbols: Boolean,
                                 abstractSymbols: Boolean,
                                 strictScope: Boolean,
                                 scope: List<WebSymbolsScope>): List<WebSymbol> =
    runNameMatchQuery(path, WebSymbolsNameMatchQueryParams(this, virtualSymbols, abstractSymbols, strictScope), scope)

  override fun runCodeCompletionQuery(path: List<WebSymbolQualifiedName>,
                                      position: Int,
                                      virtualSymbols: Boolean,
                                      scope: List<WebSymbolsScope>): List<WebSymbolCodeCompletionItem> =
    runCodeCompletionQuery(path, WebSymbolsCodeCompletionQueryParams(this, position, virtualSymbols), scope)

  override fun withNameConversionRules(rules: List<WebSymbolNameConversionRules>): WebSymbolsQueryExecutor =
    if (rules.isEmpty())
      this
    else
      WebSymbolsQueryExecutorImpl(rootScope, namesProvider.withRules(rules), resultsCustomizer, context, allowResolve)

  internal fun runNameMatchQuery(path: List<WebSymbolQualifiedName>, queryParams: WebSymbolsNameMatchQueryParams,
                                 scope: List<WebSymbolsScope>): List<WebSymbol> =
    runQuery(path, queryParams, scope) { finalContext: Collection<WebSymbolsScope>,
                                         qualifiedName: WebSymbolQualifiedName,
                                         params: WebSymbolsNameMatchQueryParams ->
      val kind = qualifiedName.kind
      val namespace = qualifiedName.namespace

      val result = finalContext
        .takeLastUntilExclusiveScopeFor(namespace, kind)
        .asSequence()
        .flatMap { scope ->
          scope.getSymbols(namespace, kind, qualifiedName.name.takeIf { it.isNotEmpty() }, params, Stack(finalContext))
        }
        .filterIsInstance<WebSymbol>()
        .filter { it !is WebSymbolMatch || it.nameSegments.size > 1 || (it.nameSegments.isNotEmpty() && it.nameSegments[0].problem == null) }
        .distinct()
        .toList()
        .let { list ->
          if (list.isNotEmpty())
            resultsCustomizer.apply(list, params.strictScope, namespace, kind, qualifiedName.name.takeIf { it.isNotEmpty() })
          else list
        }
        .let {
          if (it.isNotEmpty() && qualifiedName.name.isNotEmpty())
            it.selectBest(WebSymbol::nameSegments, WebSymbol::priority, WebSymbol::extension)
          else it
        }
      result
    }

  internal fun runCodeCompletionQuery(path: List<WebSymbolQualifiedName>, queryParams: WebSymbolsCodeCompletionQueryParams,
                                      scope: List<WebSymbolsScope>): List<WebSymbolCodeCompletionItem> =
    runQuery(path, queryParams, scope) { finalContext: Collection<WebSymbolsScope>,
                                         pathSection: WebSymbolQualifiedName,
                                         params: WebSymbolsCodeCompletionQueryParams ->
      var proximityBase = 0
      var nextProximityBase = 0
      var previousName: String? = null
      val pos = params.position
      val result = finalContext
        .takeLastUntilExclusiveScopeFor(pathSection.namespace, pathSection.kind)
        .asSequence()
        .flatMap { scope ->
          if (scope !is WebSymbol || !scope.extension || scope.name != previousName) {
            previousName = (scope as? WebSymbol)?.name
            proximityBase = nextProximityBase
          }
          scope.getCodeCompletions(pathSection.namespace, pathSection.kind, pathSection.name, params, Stack(finalContext))
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
        .mapNotNull { this.resultsCustomizer.apply(it, pathSection.namespace, pathSection.kind) }
        .toList()
        .sortAndDeduplicate()
      result
    }


  override fun getModificationCount(): Long =
    rootScope.sumOf { it.modificationCount } + namesProvider.modificationCount + resultsCustomizer.modificationCount

  private fun <T, P : WebSymbolsQueryParams> runQuery(
    path: List<WebSymbolQualifiedName>,
    params: P,
    initialScope: List<WebSymbolsScope>,
    finalProcessor: (
      context: Collection<WebSymbolsScope>,
      pathSection: WebSymbolQualifiedName,
      params: P,
    ) -> List<T>): List<T> {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    if (path.isEmpty()) return emptyList()

    val scope = rootScope.toMutableSet()
    initialScope.flatMapTo(scope) {
      if (it is WebSymbol)
        it.queryScope
      else
        listOf(it)
    }
    return RecursionManager.doPreventingRecursion(Pair(path, params.virtualSymbols), false) {
      val contextQueryParams = WebSymbolsNameMatchQueryParams(this, true, false)
      var i = 0
      while (i < path.size - 1) {
        val qName = path[i++]
        if (qName.name.isEmpty()) return@doPreventingRecursion emptyList()
        val scopeSymbols = scope.flatMap {
          it.getSymbols(qName.namespace, qName.kind, qName.name, contextQueryParams, Stack(scope))
        }
        scopeSymbols.flatMapTo(scope) {
          if (it is WebSymbol)
            it.queryScope
          else
            listOf(it)
        }
      }
      val lastSection = path.last()
      finalProcessor(scope, lastSection, params)
    } ?: run {
      thisLogger().warn("Recursive Web Symbols query: ${path.joinToString("/", "/")} with virtualSymbols=${params.virtualSymbols}.\n" +
                        "Context: " + initialScope.filterIsInstance<WebSymbol>().map { it.kind + "/" + it.name })
      emptyList()
    }
  }

  private fun Collection<WebSymbolsScope>.takeLastUntilExclusiveScopeFor(namespace: SymbolNamespace,
                                                                         kind: String): List<WebSymbolsScope> =
    toList()
      .let { list ->
        list.subList(max(0, list.indexOfLast { it.isExclusiveFor(namespace, kind) }), list.size)
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

}

