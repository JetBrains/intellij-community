// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.model.Pointer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.utils.getDefaultCodeCompletions
import com.intellij.polySymbols.utils.match
import com.intellij.util.containers.Stack

/**
 * Poly Symbols are contained within a loose model built from Poly Symbols scopes, each time anew for a particular context.
 * Each Poly Symbol is also a [PolySymbolsScope] and it can contain other Poly Symbols.
 * For instance an HTML element symbol would contain some HTML attributes symbols,
 * or a JavaScript class symbol would contain fields and methods symbols.
 *
 * When configuring queries, Poly Symbols scope are added to the list to create an initial scope for symbols resolve.
 *
 * When implementing a scope, which contains many elements you should extend [com.intellij.polySymbols.utils.PolySymbolsScopeWithCache],
 * which caches the list of symbols and uses efficient cache to speed up queries. When extending the class,
 * you only need to override the initialize method and provide parameters to the super constructor to specify how results should be cached.
 *
 * See also [Model Queries](https://plugins.jetbrains.com/docs/intellij/websymbols-implementation.html#model-queries) topic
 * to learn how queries are performed.
 *
 */
interface PolySymbolsScope : ModificationTracker {

  fun createPointer(): Pointer<out PolySymbolsScope>

  /**
   * Returns symbols within the scope, which matches provided namespace, kind and name.
   * Use [match] to match Poly Symbols in the scope against provided name.
   *
   * If the scope contains many symbols, or results should be cached consider extending [com.intellij.polySymbols.utils.PolySymbolsScopeWithCache].
   *
   */
  fun getMatchingSymbols(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolsNameMatchQueryParams,
    scope: Stack<PolySymbolsScope>,
  ): List<PolySymbol> =
    getSymbols(qualifiedName.qualifiedKind,
               PolySymbolsListSymbolsQueryParams.create(
                 params.queryExecutor, expandPatterns = false) {
                 strictScope(params.strictScope)
                 copyFiltersFrom(params)
               }, scope)
      .flatMap { it.match(qualifiedName.name, params, scope) }

  /**
   * Returns symbols of a particular kind and from particular namespace within the scope, including symbols with patterns.
   * No pattern evaluation should happen on symbols.
   *
   * If the scope contains many symbols, or results should be cached consider extending [com.intellij.polySymbols.utils.PolySymbolsScopeWithCache].
   */
  fun getSymbols(
    qualifiedKind: PolySymbolQualifiedKind,
    params: PolySymbolsListSymbolsQueryParams,
    scope: Stack<PolySymbolsScope>,
  ): List<PolySymbol> =
    emptyList()

  /**
   * Returns code completions for symbols within the scope.
   *
   * Use [com.intellij.polySymbols.utils.toCodeCompletionItems] to create code completions from `PolySymbol`s in the scope.
   *
   * If the scope contains many symbols, or results should be cached consider extending [com.intellij.polySymbols.utils.PolySymbolsScopeWithCache].
   *
   * Default implementation calls `getSymbols` and runs [com.intellij.polySymbols.utils.toCodeCompletionItems] on each symbol.
   */
  fun getCodeCompletions(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolsCodeCompletionQueryParams,
    scope: Stack<PolySymbolsScope>,
  ): List<PolySymbolCodeCompletionItem> =
    getDefaultCodeCompletions(qualifiedName, params, scope)

  /**
   * When scope is exclusive for a particular namespace and kind, resolve will not continue down the stack during pattern matching.
   */
  fun isExclusiveFor(qualifiedKind: PolySymbolQualifiedKind): Boolean =
    false

}