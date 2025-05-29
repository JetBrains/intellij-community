// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.model.Pointer
import com.intellij.util.containers.Stack
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.PolySymbolsScope
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem

/**
 * A special purpose scope, which provides other scopes, possibly calling a [PolySymbolsQueryExecutor] to retrieve them.
 * This scope is useful if your [PolySymbolsQueryConfigurator] needs to provide scopes based on the location and these
 * in turn require to query the model. It can also be added as an additional scope to any PolySymbol query, or be used
 * just to encompass logic related to building a list of scopes. [PolySymbolsCompoundScope] cannot be nested within each
 * other to prevent any recursive inclusion problems.
 */
abstract class PolySymbolsCompoundScope : PolySymbolsScope {

  protected abstract fun build(queryExecutor: PolySymbolsQueryExecutor,
                               consumer: (PolySymbolsScope) -> Unit)

  fun getScopes(queryExecutor: PolySymbolsQueryExecutor): List<PolySymbolsScope> {
    if (requiresResolve() && !queryExecutor.allowResolve) return emptyList()
    val list = mutableListOf<PolySymbolsScope>()
    build(queryExecutor) {
      if (it is PolySymbolsCompoundScope)
        throw IllegalArgumentException("PolySymbolsCompoundScope cannot be nested: $it")
      if (it is PolySymbol)
        list.addAll(it.queryScope)
      else
        list.add(it)
    }
    return list
  }

  protected open fun requiresResolve(): Boolean = true

  final override fun getMatchingSymbols(qualifiedName: PolySymbolQualifiedName,
                                        params: PolySymbolsNameMatchQueryParams,
                                        scope: Stack<PolySymbolsScope>): List<PolySymbol> =
    throw UnsupportedOperationException("PolySymbolsCompoundScope must be queried through PolySymbolQueryExecutor.")

  final override fun getSymbols(qualifiedKind: PolySymbolQualifiedKind,
                                params: PolySymbolsListSymbolsQueryParams,
                                scope: Stack<PolySymbolsScope>): List<PolySymbolsScope> =
    throw UnsupportedOperationException("PolySymbolsCompoundScope must be queried through PolySymbolQueryExecutor.")

  final override fun getCodeCompletions(qualifiedName: PolySymbolQualifiedName,
                                        params: PolySymbolsCodeCompletionQueryParams,
                                        scope: Stack<PolySymbolsScope>): List<PolySymbolCodeCompletionItem> =
    throw UnsupportedOperationException("PolySymbolsCompoundScope must be queried through PolySymbolQueryExecutor.")

  final override fun isExclusiveFor(qualifiedKind: PolySymbolQualifiedKind): Boolean =
    throw UnsupportedOperationException("PolySymbolsCompoundScope must be queried through PolySymbolQueryExecutor.")

  abstract override fun createPointer(): Pointer<out PolySymbolsCompoundScope>

  final override fun getModificationCount(): Long = 0

}