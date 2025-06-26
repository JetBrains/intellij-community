// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query.impl

import com.intellij.model.Pointer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.RecursionManager
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.impl.filterByQueryParams
import com.intellij.polySymbols.impl.selectBest
import com.intellij.polySymbols.query.*
import com.intellij.polySymbols.utils.*
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import com.intellij.util.PlatformUtils
import com.intellij.util.SmartList
import com.intellij.util.applyIf
import com.intellij.util.asSafely
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus
import kotlin.math.max
import kotlin.math.min

@ApiStatus.Internal
class PolySymbolQueryExecutorImpl(
  override val location: PsiElement?,
  rootScope: List<PolySymbolScope>,
  override val namesProvider: PolySymbolNamesProvider,
  override val resultsCustomizer: PolySymbolQueryResultsCustomizer,
  override val context: PolyContext,
  allowResolve: Boolean,
) : PolySymbolQueryExecutor {

  override val allowResolve: Boolean = if (PlatformUtils.isJetBrainsClient()) false else allowResolve

  private val rootScope: List<PolySymbolScope> = initializeCompoundScopes(rootScope)
  private var nestingLevel: Int = 0

  override var keepUnresolvedTopLevelReferences: Boolean = false

  override fun hashCode(): Int {
    var result = location.hashCode()
    result = 31 * result + rootScope.hashCode()
    result = 31 * result + context.hashCode()
    result = 31 * result + namesProvider.hashCode()
    result = 31 * result + resultsCustomizer.hashCode()
    return result
  }

  override fun equals(other: Any?): Boolean =
    other === this ||
    other is PolySymbolQueryExecutorImpl
    && other.location == location
    && other.context == context
    && other.rootScope == rootScope
    && other.namesProvider == namesProvider
    && other.resultsCustomizer == resultsCustomizer

  override fun createPointer(): Pointer<PolySymbolQueryExecutor> {
    val locationPtr = this.location?.createSmartPointer()
    val namesProviderPtr = this.namesProvider.createPointer()
    val context = this.context
    val allowResolve = this.allowResolve
    val scopePtr = this.resultsCustomizer.createPointer()
    val rootScopePointers = this.rootScope.map { it.createPointer() }
    return Pointer<PolySymbolQueryExecutor> {
      @Suppress("UNCHECKED_CAST")
      val rootScope = rootScopePointers.map { it.dereference() }
                        .takeIf { it.all { c -> c != null } } as? List<PolySymbolScope>
                      ?: return@Pointer null

      val namesProvider = namesProviderPtr.dereference()
                          ?: return@Pointer null

      val scope = scopePtr.dereference()
                  ?: return@Pointer null
      val location = locationPtr?.let { it.dereference() ?: return@Pointer null }
      PolySymbolQueryExecutorImpl(location, rootScope, namesProvider, scope, context, allowResolve)
    }
  }

  override fun nameMatchQuery(
    path: List<PolySymbolQualifiedName>,
  ): PolySymbolQueryExecutor.NameMatchQueryBuilder =
    NameMatchQueryBuilderImpl(path)

  override fun listSymbolsQuery(
    path: List<PolySymbolQualifiedName>,
    qualifiedKind: PolySymbolQualifiedKind,
    expandPatterns: Boolean,
  ): PolySymbolQueryExecutor.ListSymbolsQueryBuilder =
    ListSymbolsQueryBuilderImpl(path, qualifiedKind, expandPatterns)

  override fun codeCompletionQuery(
    path: List<PolySymbolQualifiedName>,
    position: Int,
  ): PolySymbolQueryExecutor.CodeCompletionQueryBuilder =
    CodeCompletionQueryBuilderImpl(path, position)

  @Suppress("UNCHECKED_CAST")
  abstract class AbstractQueryBuilderImpl<T> : AbstractQueryParamsBuilderImpl<T>(), PolySymbolQueryExecutor.QueryBuilder<T> {
    protected val additionalScope: MutableList<PolySymbolScope> = SmartList()

    override fun additionalScope(scope: PolySymbolScope): T {
      additionalScope.add(scope)
      return this as T
    }

    override fun additionalScope(vararg scopes: PolySymbolScope): T {
      additionalScope.addAll(scopes)
      return this as T
    }

    override fun additionalScope(scopes: Collection<PolySymbolScope>): T {
      additionalScope.addAll(scopes)
      return this as T
    }

    override fun additionalScope(stack: PolySymbolQueryStack): T {
      additionalScope.addAll(stack.toList())
      return this as T
    }

  }

  private inner class ListSymbolsQueryBuilderImpl(
    private val path: List<PolySymbolQualifiedName>,
    private val qualifiedKind: PolySymbolQualifiedKind,
    private val expandPatterns: Boolean,
  ) : PolySymbolQueryExecutor.ListSymbolsQueryBuilder, AbstractQueryBuilderImpl<PolySymbolQueryExecutor.ListSymbolsQueryBuilder>() {

    private var strictScope: Boolean = false

    override fun strictScope(value: Boolean): PolySymbolQueryExecutor.ListSymbolsQueryBuilder {
      this.strictScope = value
      return this
    }

    override fun run(): List<PolySymbol> =
      runListSymbolsQuery(path + qualifiedKind.withName(""),
                          PolySymbolListSymbolsQueryParamsData(
                            this@PolySymbolQueryExecutorImpl,
                            expandPatterns = expandPatterns,
                            strictScope = strictScope,
                            requiredModifiers = requiredModifiers.toList(),
                            excludeModifiers = excludeModifiers.toList(),
                          ), additionalScope.toList())
  }

  private inner class NameMatchQueryBuilderImpl(
    private val path: List<PolySymbolQualifiedName>,
  ) : PolySymbolQueryExecutor.NameMatchQueryBuilder, AbstractQueryBuilderImpl<PolySymbolQueryExecutor.NameMatchQueryBuilder>() {

    private var strictScope: Boolean = false

    override fun strictScope(value: Boolean): PolySymbolQueryExecutor.NameMatchQueryBuilder {
      this.strictScope = value
      return this
    }

    override fun run(): List<PolySymbol> =
      runNameMatchQuery(path,
                        PolySymbolNameMatchQueryParamsData(
                          this@PolySymbolQueryExecutorImpl,
                          strictScope = strictScope,
                          requiredModifiers = requiredModifiers.toList(),
                          excludeModifiers = excludeModifiers.toList(),
                        ), additionalScope.toList())
  }

  private inner class CodeCompletionQueryBuilderImpl(
    private val path: List<PolySymbolQualifiedName>,
    private val position: Int,
  ) : PolySymbolQueryExecutor.CodeCompletionQueryBuilder, AbstractQueryBuilderImpl<PolySymbolQueryExecutor.CodeCompletionQueryBuilder>() {

    override fun run(): List<PolySymbolCodeCompletionItem> =
      runCodeCompletionQuery(path,
                             PolySymbolCodeCompletionQueryParamsData(
                               this@PolySymbolQueryExecutorImpl,
                               position = position,
                               requiredModifiers = requiredModifiers.toList(),
                               excludeModifiers = excludeModifiers.toList(),
                             ), additionalScope.toList())
  }

  override fun withNameConversionRules(rules: List<PolySymbolNameConversionRules>): PolySymbolQueryExecutor =
    if (rules.isEmpty())
      this
    else
      PolySymbolQueryExecutorImpl(location, rootScope, namesProvider.withRules(rules), resultsCustomizer, context, allowResolve)

  override fun hasExclusiveScopeFor(qualifiedKind: PolySymbolQualifiedKind, scope: List<PolySymbolScope>): Boolean {
    return buildQueryScope(scope).any { it.isExclusiveFor(qualifiedKind) }
  }

  private fun initializeCompoundScopes(rootScope: List<PolySymbolScope>): List<PolySymbolScope> {
    if (rootScope.any { it is PolySymbolCompoundScope }) {
      val compoundScopeQueryExecutor = PolySymbolQueryExecutorImpl(
        location,
        rootScope.filter { it !is PolySymbolCompoundScope },
        namesProvider, resultsCustomizer, context, allowResolve
      )
      return rootScope.flatMap {
        if (it is PolySymbolCompoundScope) {
          it.getScopes(compoundScopeQueryExecutor)
        }
        else {
          listOf(it)
        }
      }
    }
    else return rootScope
  }

  private fun buildQueryScope(additionalScope: List<PolySymbolScope>): MutableSet<PolySymbolScope> {
    val finalScope = rootScope.toMutableSet()
    additionalScope.flatMapTo(finalScope) {
      when (it) {
        is PolySymbolCompoundScope -> it.getScopes(this)
        is PolySymbol -> it.queryScope
        else -> listOf(it)
      }
    }
    return finalScope
  }

  private fun runNameMatchQuery(
    path: List<PolySymbolQualifiedName>,
    queryParams: PolySymbolNameMatchQueryParams,
    additionalScope: List<PolySymbolScope>,
  ): List<PolySymbol> =
    runQuery(path, queryParams, additionalScope) {
      finalContext: Collection<PolySymbolScope>,
      qualifiedName: PolySymbolQualifiedName,
      params: PolySymbolNameMatchQueryParams,
      ->
      val result = finalContext
        .takeLastUntilExclusiveScopeFor(qualifiedName.qualifiedKind)
        .asSequence()
        .flatMap { scope ->
          ProgressManager.checkCanceled()
          val prev = keepUnresolvedTopLevelReferences
          keepUnresolvedTopLevelReferences = false
          try {
            scope.getMatchingSymbols(qualifiedName, params, PolySymbolQueryStack(finalContext))
          }
          finally {
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
    path: List<PolySymbolQualifiedName>, queryParams: PolySymbolListSymbolsQueryParams,
    additionalScope: List<PolySymbolScope>,
  ): List<PolySymbol> =
    runQuery(path, queryParams, additionalScope) {
      finalContext: Collection<PolySymbolScope>,
      qualifiedName: PolySymbolQualifiedName,
      params: PolySymbolListSymbolsQueryParams,
      ->
      val result = finalContext
        .takeLastUntilExclusiveScopeFor(qualifiedName.qualifiedKind)
        .asSequence()
        .flatMap { scope ->
          ProgressManager.checkCanceled()
          scope.getSymbols(qualifiedName.qualifiedKind, params, PolySymbolQueryStack(finalContext))
        }
        .distinct()
        .filterByQueryParams(params)
        .applyIf(params.expandPatterns) {
          flatMap { it.expandPattern(finalContext, params) }
        }
        .groupBy {
          queryParams.queryExecutor.namesProvider
            .getNames(qualifiedName.withName(it.name), PolySymbolNamesProvider.Target.NAMES_MAP_STORAGE).firstOrNull()
          ?: it.name
        }
        .flatMap { (name, list) ->
          ProgressManager.checkCanceled()
          list
            .customizeMatches(params.strictScope, qualifiedName.withName(name))
            .selectBest(PolySymbol::nameSegments, PolySymbol::priority, PolySymbol::extension)
            .applyIf(params.expandPatterns) {
              asSingleSymbol()
                ?.let { symbol ->
                  params.queryExecutor.namesProvider
                    .getNames(symbol.qualifiedName, PolySymbolNamesProvider.Target.CODE_COMPLETION_VARIANTS)
                    .firstOrNull()
                    ?.let { name -> listOf(symbol.withMatchedName(name)) }
                }
              ?: emptyList()
            }
        }
      result
    }

  private fun runCodeCompletionQuery(
    path: List<PolySymbolQualifiedName>, queryParams: PolySymbolCodeCompletionQueryParams,
    additionalScope: List<PolySymbolScope>,
  ): List<PolySymbolCodeCompletionItem> =
    runQuery(path, queryParams, additionalScope) {
      finalContext: Collection<PolySymbolScope>,
      pathSection: PolySymbolQualifiedName,
      params: PolySymbolCodeCompletionQueryParams,
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
          scope.getCodeCompletions(pathSection, params, PolySymbolQueryStack(finalContext))
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
  private fun <T, P : PolySymbolQueryParams> runQuery(
    path: List<PolySymbolQualifiedName>,
    params: P,
    additionalScope: List<PolySymbolScope>,
    finalProcessor: (
      context: Collection<PolySymbolScope>,
      pathSection: PolySymbolQualifiedName,
      params: P,
    ) -> List<T>,
  ): List<T> {
    ProgressManager.checkCanceled()
    if (path.isEmpty()) return emptyList()

    val scope = buildQueryScope(additionalScope)
    return RecursionManager.doPreventingRecursion(Pair(path, params.recursionKey), false) {
      val contextQueryParams = PolySymbolNameMatchQueryParams.create(this) {
        exclude(PolySymbolModifier.ABSTRACT)
      }
      val publisher = if (nestingLevel++ == 0)
        ApplicationManager.getApplication().messageBus.syncPublisher(PolySymbolQueryExecutorListener.TOPIC)
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
                it.getMatchingSymbols(qName, contextQueryParams, PolySymbolQueryStack(scope))
              }
              finally {
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
      thisLogger().warn("Recursive Poly Symbols query: ${path.joinToString("/")} with params=${params.recursionKey}.\n" +
                        "Root scope: " + rootScope.map {
        it.asSafely<PolySymbol>()?.let { symbol -> "${symbol.qualifiedKind}/${symbol.name}" } ?: it
      } + "\n" +
                        "Additional scope: " + additionalScope.map {
        it.asSafely<PolySymbol>()?.let { symbol -> "${symbol.qualifiedKind}/${symbol.name}" } ?: it
      })
      emptyList()
    }
  }

  private fun Collection<PolySymbolScope>.takeLastUntilExclusiveScopeFor(qualifiedKind: PolySymbolQualifiedKind): List<PolySymbolScope> =
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
            .sortedWith(Comparator.comparing { it: PolySymbolCodeCompletionItem -> -(it.priority ?: PolySymbol.Priority.NORMAL).value }
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
    context: Collection<PolySymbolScope>,
    params: PolySymbolListSymbolsQueryParams,
  ): List<PolySymbol> =
    if (this is PolySymbolWithPattern)
      pattern
        .list(this, PolySymbolQueryStack(context + queryScope), params)
        .map {
          PolySymbolMatch.create(it.name, it.segments, qualifiedKind, origin)
        }
    else
      listOf(this)

  private fun List<PolySymbol>.customizeMatches(strict: Boolean, qualifiedName: PolySymbolQualifiedName): List<PolySymbol> =
    if (isEmpty())
      this
    else {
      ProgressManager.checkCanceled()
      resultsCustomizer.apply(this, strict, qualifiedName)
    }

  private val PolySymbolQueryParams.recursionKey: List<Any>
    get() {
      val result = SmartList<Any>()
      result.add(requiredModifiers)
      result.add(excludeModifiers)
      return result
    }
}
