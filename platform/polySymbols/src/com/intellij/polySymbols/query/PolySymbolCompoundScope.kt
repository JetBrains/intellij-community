// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.model.Pointer
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem

/**
 * A special purpose scope, which provides other scopes, possibly calling a [PolySymbolQueryExecutor] to retrieve them.
 * This scope is useful if your [PolySymbolQueryConfigurator] needs to provide scopes based on the location and these
 * in turn require to query the model. It can also be added as an additional scope to any PolySymbol query, or be used
 * just to encompass logic related to building a list of scopes. [PolySymbolCompoundScope] cannot be nested within each
 * other to prevent any recursive inclusion problems.
 */
abstract class PolySymbolCompoundScope : PolySymbolScope {

  protected abstract fun build(
    queryExecutor: PolySymbolQueryExecutor,
    consumer: (PolySymbolScope) -> Unit,
  )

  fun getScopes(queryExecutor: PolySymbolQueryExecutor): List<PolySymbolScope> {
    if (requiresResolve() && !queryExecutor.allowResolve) return emptyList()
    val list = mutableListOf<PolySymbolScope>()
    build(queryExecutor) {
      if (it is PolySymbolCompoundScope)
        throw IllegalArgumentException("PolySymbolCompoundScope cannot be nested: $it")
      if (it is PolySymbol)
        list.addAll(it.queryScope)
      else
        list.add(it)
    }
    return list
  }

  protected open fun requiresResolve(): Boolean = true

  final override fun getMatchingSymbols(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolNameMatchQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbol> =
    throw UnsupportedOperationException("PolySymbolCompoundScope must be queried through PolySymbolQueryExecutor.")

  final override fun getSymbols(
    qualifiedKind: PolySymbolQualifiedKind,
    params: PolySymbolListSymbolsQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbol> =
    throw UnsupportedOperationException("PolySymbolCompoundScope must be queried through PolySymbolQueryExecutor.")

  final override fun getCodeCompletions(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolCodeCompletionQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbolCodeCompletionItem> =
    throw UnsupportedOperationException("PolySymbolCompoundScope must be queried through PolySymbolQueryExecutor.")

  final override fun isExclusiveFor(qualifiedKind: PolySymbolQualifiedKind): Boolean =
    throw UnsupportedOperationException("PolySymbolCompoundScope must be queried through PolySymbolQueryExecutor.")

  abstract override fun createPointer(): Pointer<out PolySymbolCompoundScope>

  final override fun getModificationCount(): Long = 0

}