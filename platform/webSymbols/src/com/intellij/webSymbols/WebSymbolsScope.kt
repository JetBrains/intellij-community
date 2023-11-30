// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.model.Pointer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.query.WebSymbolsCodeCompletionQueryParams
import com.intellij.webSymbols.query.WebSymbolsListSymbolsQueryParams
import com.intellij.webSymbols.query.WebSymbolsNameMatchQueryParams
import com.intellij.webSymbols.utils.getDefaultCodeCompletions
import com.intellij.webSymbols.utils.match
import com.intellij.webSymbols.utils.toCodeCompletionItems

/**
 * Web Symbols are contained within a loose model built from Web Symbols scopes, each time anew for a particular context.
 * Each Web Symbol is also a [WebSymbolsScope] and it can contain other Web Symbols.
 * For instance an HTML element symbol would contain some HTML attributes symbols,
 * or a JavaScript class symbol would contain fields and methods symbols.
 *
 * When configuring queries, Web Symbols scope are added to the list to create an initial scope for symbols resolve.
 *
 * When implementing a scope, which contains many elements you should extend [WebSymbolsScopeWithCache],
 * which caches the list of symbols and uses efficient cache to speed up queries. When extending the class,
 * you only need to override the initialize method and provide parameters to the super constructor to specify how results should be cached.
 *
 * See also [Model Queries](https://plugins.jetbrains.com/docs/intellij/websymbols-implementation.html#model-queries) topic
 * to learn how queries are performed.
 *
 */
interface WebSymbolsScope : ModificationTracker {

  fun createPointer(): Pointer<out WebSymbolsScope>

  /**
   * Returns symbols within the scope, which matches provided namespace, kind and name.
   * Use [WebSymbol.match] to match Web Symbols in the scope against provided name.
   *
   * If the scope contains many symbols, or results should be cached consider extending [WebSymbolsScopeWithCache].
   *
   */
  fun getMatchingSymbols(qualifiedName: WebSymbolQualifiedName,
                         params: WebSymbolsNameMatchQueryParams,
                         scope: Stack<WebSymbolsScope>): List<WebSymbol> =
    getSymbols(qualifiedName.qualifiedKind,
               WebSymbolsListSymbolsQueryParams(params.queryExecutor, expandPatterns = false, virtualSymbols = params.virtualSymbols,
                                                abstractSymbols = params.abstractSymbols, strictScope = params.strictScope),
               scope)
      .filterIsInstance<WebSymbol>()
      .flatMap { it.match(qualifiedName.name, params, scope) }

  /**
   * Returns symbols of a particular kind and from particular namespace within the scope, including symbols with patterns.
   * No pattern evaluation should happen on symbols.
   *
   * If the scope contains many symbols, or results should be cached consider extending [WebSymbolsScopeWithCache].
   */
  fun getSymbols(qualifiedKind: WebSymbolQualifiedKind,
                 params: WebSymbolsListSymbolsQueryParams,
                 scope: Stack<WebSymbolsScope>): List<WebSymbolsScope> =
    emptyList()

  /**
   * Returns code completions for symbols within the scope.
   *
   * Use [WebSymbol.toCodeCompletionItems] to create code completions from `WebSymbol`s in the scope.
   *
   * If the scope contains many symbols, or results should be cached consider extending [WebSymbolsScopeWithCache].
   *
   * Default implementation calls `getSymbols` and runs [WebSymbol.toCodeCompletionItems] on each symbol.
   */
  fun getCodeCompletions(qualifiedName: WebSymbolQualifiedName,
                         params: WebSymbolsCodeCompletionQueryParams,
                         scope: Stack<WebSymbolsScope>): List<WebSymbolCodeCompletionItem> =
    getDefaultCodeCompletions(qualifiedName, params, scope)

  /**
   * When scope is exclusive for a particular namespace and kind, resolve will not continue down the stack during pattern matching.
   */
  fun isExclusiveFor(qualifiedKind: WebSymbolQualifiedKind): Boolean =
    false

}