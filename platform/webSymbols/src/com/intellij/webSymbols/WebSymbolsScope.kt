// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.model.Pointer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.query.WebSymbolsCodeCompletionQueryParams
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
   * Returns symbols within the scope. If provided name is `null`, no pattern evaluation should happen
   * and all symbols of a particular kind and from particular namespace should be returned.
   *
   * Use [WebSymbol.match] to match Web Symbols in the scope against provided name.
   *
   * If the scope contains many symbols, or results should be cached consider extending [WebSymbolsScopeWithCache].
   *
   */
  fun getSymbols(namespace: SymbolNamespace,
                 kind: SymbolKind,
                 name: String?,
                 params: WebSymbolsNameMatchQueryParams,
                 scope: Stack<WebSymbolsScope>): List<WebSymbolsScope> =
    emptyList()

  /**
   * Returns code completions for symbols within the scope.
   *
   * Use [WebSymbol.toCodeCompletionItems] to create code completions from `WebSymbol`s in the scope.
   *
   * If the scope contains many symbols, or results should be cached consider extending [WebSymbolsScopeWithCache].
   *
   * Default implementation calls `getSymbols` with `null` `name` and runs [WebSymbol.toCodeCompletionItems] on each symbol.
   */
  fun getCodeCompletions(namespace: SymbolNamespace,
                         kind: SymbolKind,
                         name: String?,
                         params: WebSymbolsCodeCompletionQueryParams,
                         scope: Stack<WebSymbolsScope>): List<WebSymbolCodeCompletionItem> =
    getDefaultCodeCompletions(namespace, kind, name, params, scope)

  /**
   * When scope is exclusive for a particular namespace and kind, resolve will not continue down the stack during pattern matching.
   */
  fun isExclusiveFor(namespace: SymbolNamespace, kind: SymbolKind): Boolean =
    false

}