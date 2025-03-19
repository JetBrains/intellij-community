// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.model.Pointer
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolQualifiedKind
import com.intellij.webSymbols.WebSymbolQualifiedName
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem

/**
 * A special purpose scope, which provides other scopes, possibly calling a [WebSymbolsQueryExecutor] to retrieve them.
 * This scope is useful if your [WebSymbolsQueryConfigurator] needs to provide scopes based on the location and these
 * in turn require to query the model. It can also be added as an additional scope to any WebSymbol query, or be used
 * just to encompass logic related to building a list of scopes. [WebSymbolsCompoundScope] cannot be nested within each
 * other to prevent any recursive inclusion problems.
 */
abstract class WebSymbolsCompoundScope : WebSymbolsScope {

  protected abstract fun build(queryExecutor: WebSymbolsQueryExecutor,
                               consumer: (WebSymbolsScope) -> Unit)

  fun getScopes(queryExecutor: WebSymbolsQueryExecutor): List<WebSymbolsScope> {
    if (requiresResolve() && !queryExecutor.allowResolve) return emptyList()
    val list = mutableListOf<WebSymbolsScope>()
    build(queryExecutor) {
      if (it is WebSymbolsCompoundScope)
        throw IllegalArgumentException("WebSymbolsCompoundScope cannot be nested: $it")
      if (it is WebSymbol)
        list.addAll(it.queryScope)
      else
        list.add(it)
    }
    return list
  }

  protected open fun requiresResolve(): Boolean = true

  final override fun getMatchingSymbols(qualifiedName: WebSymbolQualifiedName,
                                        params: WebSymbolsNameMatchQueryParams,
                                        scope: Stack<WebSymbolsScope>): List<WebSymbol> =
    throw UnsupportedOperationException("WebSymbolsCompoundScope must be queried through WebSymbolQueryExecutor.")

  final override fun getSymbols(qualifiedKind: WebSymbolQualifiedKind,
                                params: WebSymbolsListSymbolsQueryParams,
                                scope: Stack<WebSymbolsScope>): List<WebSymbolsScope> =
    throw UnsupportedOperationException("WebSymbolsCompoundScope must be queried through WebSymbolQueryExecutor.")

  final override fun getCodeCompletions(qualifiedName: WebSymbolQualifiedName,
                                        params: WebSymbolsCodeCompletionQueryParams,
                                        scope: Stack<WebSymbolsScope>): List<WebSymbolCodeCompletionItem> =
    throw UnsupportedOperationException("WebSymbolsCompoundScope must be queried through WebSymbolQueryExecutor.")

  final override fun isExclusiveFor(qualifiedKind: WebSymbolQualifiedKind): Boolean =
    throw UnsupportedOperationException("WebSymbolsCompoundScope must be queried through WebSymbolQueryExecutor.")

  abstract override fun createPointer(): Pointer<out WebSymbolsCompoundScope>

  final override fun getModificationCount(): Long = 0

}