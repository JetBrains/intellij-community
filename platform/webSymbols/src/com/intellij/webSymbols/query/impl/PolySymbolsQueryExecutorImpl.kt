// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query.impl

import com.intellij.model.Pointer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import com.intellij.util.applyIf
import com.intellij.util.asSafely
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.PolySymbol
import com.intellij.webSymbols.PolySymbolQualifiedKind
import com.intellij.webSymbols.PolySymbolQualifiedName
import com.intellij.webSymbols.PolySymbolsScope
import com.intellij.webSymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.webSymbols.context.PolyContext
import com.intellij.webSymbols.impl.filterByQueryParams
import com.intellij.webSymbols.impl.selectBest
import com.intellij.webSymbols.query.*
import com.intellij.webSymbols.utils.*
import org.jetbrains.annotations.ApiStatus
import java.util.*
import kotlin.math.max
import kotlin.math.min

@ApiStatus.Internal
class PolySymbolsQueryExecutorImpl(
  override val location: PsiElement?,
  rootScope: List<PolySymbolsScope>,
  override val namesProvider: WebSymbolNamesProvider,
  override val resultsCustomizer: WebSymbolsQueryResultsCustomizer,
  override val context: PolyContext,
  override val allowResolve: Boolean,
) : PolySymbolsQueryExecutor {

  private val rootScope: List<PolySymbolsScope> = initializeCompoundScopes(rootScope)
  private var nestingLevel: Int = 0

  override var keepUnresolvedTopLevelReferences: Boolean = false

  override fun hashCode(): Int =
    Objects.hash(location, rootScope, context, namesProvider, resultsCustomizer)

  override fun equals(other: Any?): Boolean =
    other === this ||
    other is PolySymbolsQueryExecutorImpl
    && other.location == location
    && other.context == context
    && other.rootScope == rootScope
    && other.namesProvider == namesProvider
    && other.resultsCustomizer == resultsCustomizer

  override fun createPointer(): Pointer<PolySymbolsQueryExecutor> {
    val locationPtr = this.location?.createSmartPointer()
    val namesProviderPtr = this.namesProvider.createPointer()
    val context = this.context
    val allowResolve = this.allowResolve
    val scopePtr = this.resultsCustomizer.createPointer()
    val rootScopePointers = this.rootScope.map { it.createPointer() }
    return Pointer<PolySymbolsQueryExecutor> {
      @Suppress("UNCHECKED_CAST")
      val rootScope = rootScopePointers.map { it.dereference() }
                        .takeIf { it.all { c -> c != null } } as? List<PolySymbolsScope>
                      ?: return@Pointer null

      val namesProvider = namesProviderPtr.dereference()
                          ?: return@Pointer null

      val scope = scopePtr.dereference()
                  ?: return@Pointer null
      val location = locationPtr?.let { it.dereference() ?: return@Pointer null }
      PolySymbolsQueryExecutorImpl(location, rootScope, namesProvider, scope, context, allowResolve)
    }
  }

  override fun runNameMatchQuery(
    path: List<PolySymbolQualifiedName>,
    virtualSymbols: Boolean,
    abstractSymbols: Boolean,
    strictScope: Boolean,
    additionalScope: List<PolySymbolsScope>,
  ): List<PolySymbol> =
    runNameMatchQuery(path, WebSymbolsNameMatchQueryParams.create(this, virtualSymbols, abstractSymbols, strictScope), additionalScope)

  override fun runListSymbolsQuery(
    path: List<PolySymbolQualifiedName>,
    qualifiedKind: PolySymbolQualifiedKind,
    expandPatterns: Boolean,
    virtualSymbols: Boolean,
    abstractSymbols: Boolean,
    strictScope: Boolean,
    additionalScope: List<PolySymbolsScope>,
  ): List<PolySymbol> =
    runListSymbolsQuery(path + qualifiedKind.withName(""),
                        WebSymbolsListSymbolsQueryParams.create(this, expandPatterns = expandPatterns, virtualSymbols = virtualSymbols,
                                                                abstractSymbols = abstractSymbols, strictScope = strictScope), additionalScope)

  override fun runCodeCompletionQuery(
    path: List<PolySymbolQualifiedName>,
    position: Int,
    virtualSymbols: Boolean,
    additionalScope: List<PolySymbolsScope>,
  ): List<PolySymbolCodeCompletionItem> =
    runCodeCompletionQuery(path, WebSymbolsCodeCompletionQueryParams.create(this, position, virtualSymbols), additionalScope)

  override fun withNameConversionRules(rules: List<WebSymbolNameConversionRules>): PolySymbolsQueryExecutor =
    if (rules.isEmpty())
      this
    else
      PolySymbolsQueryExecutorImpl(location, rootScope, namesProvider.withRules(rules), resultsCustomizer, context, allowResolve)

  override fun hasExclusiveScopeFor(qualifiedKind: PolySymbolQualifiedKind, scope: List<PolySymbolsScope>): Boolean {
    return buildQueryScope(scope).any { it.isExclusiveFor(qualifiedKind) }
  }

  private fun initializeCompoundScopes(rootScope: List<PolySymbolsScope>): List<PolySymbolsScope> {
    if (rootScope.any { it is PolySymbolsCompoundScope }) {
      val compoundScopeQueryExecutor = PolySymbolsQueryExecutorImpl(
        location,
        rootScope.filter { it !is PolySymbolsCompoundScope },
        namesProvider, resultsCustomizer, context, allowResolve
      )
      return rootScope.flatMap {
        if (it is PolySymbolsCompoundScope) {
          it.getScopes(compoundScopeQueryExecutor)
        }
        else {
          listOf(it)
        }
      }
    }
    else return rootScope
  }

  private fun buildQueryScope(additionalScope: List<PolySymbolsScope>): MutableSet<PolySymbolsScope> {
    val finalScope = rootScope.toMutableSet()
    additionalScope.flatMapTo(finalScope) {
      when (it) {
        is PolySymbolsCompoundScope -> it.getScopes(this)
        is PolySymbol -> it.queryScope
        else -> listOf(it)
      }
    }
    return finalScope
  }

  private fun runNameMatchQuery(
    path: List<PolySymbolQualifiedName>,
    queryParams: WebSymbolsNameMatchQueryParams,
    additionalScope: List<PolySymbolsScope>,
  ): List<PolySymbol> =
    runQuery(path, queryParams, additionalScope) {
        finalContext: Collection<PolySymbolsScope>,
        qualifiedName: PolySymbolQualifiedName,
        params: WebSymbolsNameMatchQueryParams,
      ->
      val result = finalContext
        .takeLastUntilExclusiveScopeFor(qualifiedName.qualifiedKind)
        .asSequence()
        .flatMap { scope ->
          ProgressManager.checkCanceled()
          val prev = keepUnresolvedTopLevelReferences
          keepUnresolvedTopLevelReferences = false
          try {
            scope.getMatchingSymbols(qualifiedName, params, Stack(finalContext))
          } finally {
            keepUnresolvedTopLevelReferences = prev
          }
        }
        .filter {
          keepUnresolvedTopLevelReferences
          || it !is PolySymbolMatch || it.nameSegments.size > 1 || (it.nameSegments.isNotEmpty() && it.nameSegments[0].problem == null)
        }
        .distinct()
        .toList()
        .customizeMatches(params.strictScope, qualifiedName)
        .selectBest(PolySymbol::nameSegments, PolySymbol::priority, PolySymbol::extension)
      result
    }

  private fun runListSymbolsQuery(
    path: List<PolySymbolQualifiedName>, queryParams: WebSymbolsListSymbolsQueryParams,
    additionalScope: List<PolySymbolsScope>,
  ): List<PolySymbol> =
    runQuery(path, queryParams, additionalScope) {
        finalContext: Collection<PolySymbolsScope>,
        qualifiedName: PolySymbolQualifiedName,
        params: WebSymbolsListSymbolsQueryParams,
      ->
      val result = finalContext
        .takeLastUntilExclusiveScopeFor(qualifiedName.qualifiedKind)
        .asSequence()
        .flatMap { scope ->
          ProgressManager.checkCanceled()
          scope.getSymbols(qualifiedName.qualifiedKind, params, Stack(finalContext))
        }
        .filterIsInstance<PolySymbol>()
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
            .selectBest(PolySymbol::nameSegments, PolySymbol::priority, PolySymbol::extension)
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
    path: List<PolySymbolQualifiedName>, queryParams: WebSymbolsCodeCompletionQueryParams,
    additionalScope: List<PolySymbolsScope>,
  ): List<PolySymbolCodeCompletionItem> =
    runQuery(path, queryParams, additionalScope) {
        finalContext: Collection<PolySymbolsScope>,
        pathSection: PolySymbolQualifiedName,
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
          if (scope !is PolySymbol || !scope.extension || scope.name != previousName) {
            previousName = (scope as? PolySymbol)?.name
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
    path: List<PolySymbolQualifiedName>,
    params: P,
    additionalScope: List<PolySymbolsScope>,
    finalProcessor: (
      context: Collection<PolySymbolsScope>,
      pathSection: PolySymbolQualifiedName,
      params: P,
    ) -> List<T>,
  ): List<T> {
    ProgressManager.checkCanceled()
    if (path.isEmpty()) return emptyList()

    val scope = buildQueryScope(additionalScope)
    return RecursionManager.doPreventingRecursion(Pair(path, params.virtualSymbols), false) {
      val contextQueryParams = WebSymbolsNameMatchQueryParams.create(this, true, false)
      val publisher = if (nestingLevel++ == 0)
        ApplicationManager.getApplication().messageBus.syncPublisher(WebSymbolsQueryExecutorListener.TOPIC)
      else
        null
      publisher?.beforeQuery(params)
      try {
        var i = 0
        while (i < path.size - 1) {
          val qName = path[i++]
          if (qName.name.isEmpty()) return@doPreventingRecursion emptyList()
          val scopeSymbols = scope
            .takeLastUntilExclusiveScopeFor(qName.qualifiedKind)
            .flatMap {
              val prev = keepUnresolvedTopLevelReferences
              keepUnresolvedTopLevelReferences = false
              try {
                it.getMatchingSymbols(qName, contextQueryParams, Stack(scope))
              } finally {
                keepUnresolvedTopLevelReferences = prev
              }
            }
          scopeSymbols.flatMapTo(scope) {
            it.queryScope
          }
        }
        val lastSection = path.last()
        finalProcessor(scope, lastSection, params)
      }
      finally {
        publisher?.afterQuery(params)
        nestingLevel--
      }
    } ?: run {
      thisLogger().warn("Recursive Web Symbols query: ${path.joinToString("/")} with virtualSymbols=${params.virtualSymbols}.\n" +
                        "Root scope: " + rootScope.map {
        it.asSafely<PolySymbol>()?.let { symbol -> symbol.kind + "/" + symbol.name } ?: it
      } + "\n" +
                        "Additional scope: " + additionalScope.map {
        it.asSafely<PolySymbol>()?.let { symbol -> symbol.kind + "/" + symbol.name } ?: it
      })
      emptyList()
    }
  }

  private fun Collection<PolySymbolsScope>.takeLastUntilExclusiveScopeFor(qualifiedKind: PolySymbolQualifiedKind): List<PolySymbolsScope> =
    toList()
      .let { list ->
        list.subList(max(0, list.indexOfLast { it.isExclusiveFor(qualifiedKind) }), list.size)
      }

  private fun List<PolySymbolCodeCompletionItem>.sortAndDeduplicate(): List<PolySymbolCodeCompletionItem> =
    groupBy { Triple(it.name, it.displayName, it.offset) }
      .mapNotNull { (_, items) ->
        if (items.size == 1) {
          items[0]
        }
        else {
          items
            .sortedWith(Comparator.comparing { it: PolySymbolCodeCompletionItem -> -(it.priority ?: PolySymbol.Priority.NORMAL).ordinal }
                          .thenComparingInt { -(it.proximity ?: 0) })
            .firstOrNull()
        }
      }

  private fun Sequence<PolySymbolCodeCompletionItem>.mapWithSymbolPriority() =
    map { item ->
      item.symbol
        ?.priority
        ?.takeIf { it > (item.priority ?: PolySymbol.Priority.LOWEST) }
        ?.let { item.withPriority(it) }
      ?: item
    }

  private fun PolySymbol.expandPattern(
    context: Stack<PolySymbolsScope>,
    params: WebSymbolsListSymbolsQueryParams,
  ): List<PolySymbol> =
    pattern?.let { pattern ->
      context.push(this)
      try {
        return pattern
          .list(this, context, params)
          .map {
            PolySymbolMatch.create(it.name, it.segments, namespace, kind, origin)
          }
      }
      finally {
        context.pop()
      }
    } ?: listOf(this)

  private fun List<PolySymbol>.customizeMatches(strict: Boolean, qualifiedName: PolySymbolQualifiedName): List<PolySymbol> =
    if (isEmpty())
      this
    else {
      ProgressManager.checkCanceled()
      resultsCustomizer.apply(this, strict, qualifiedName)
    }

}

