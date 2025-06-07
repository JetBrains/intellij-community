// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

sealed interface PolySymbolsQueryParams {

  val framework: String?
    get() = queryExecutor.framework

  val queryExecutor: PolySymbolsQueryExecutor

  val virtualSymbols: Boolean
}

sealed interface PolySymbolsListSymbolsQueryParams : PolySymbolsQueryParams {
  override val queryExecutor: PolySymbolsQueryExecutor
  val expandPatterns: Boolean
  override val virtualSymbols: Boolean
  val abstractSymbols: Boolean
  val strictScope: Boolean

  companion object {

    @JvmStatic
    @JvmOverloads
    fun create(
      queryExecutor: PolySymbolsQueryExecutor,
      expandPatterns: Boolean,
      virtualSymbols: Boolean = true
    ): PolySymbolsListSymbolsQueryParams =
      create(queryExecutor, expandPatterns, virtualSymbols, false, false)

    @JvmStatic
    fun create(
      queryExecutor: PolySymbolsQueryExecutor,
      expandPatterns: Boolean,
      virtualSymbols: Boolean = true,
      abstractSymbols: Boolean = false,
      strictScope: Boolean = false,
    ): PolySymbolsListSymbolsQueryParams =
      PolySymbolsListSymbolsQueryParamsData(queryExecutor, expandPatterns, virtualSymbols, abstractSymbols, strictScope)

  }
}


sealed interface PolySymbolsNameMatchQueryParams : PolySymbolsQueryParams {
  override val queryExecutor: PolySymbolsQueryExecutor
  override val virtualSymbols: Boolean
  val abstractSymbols: Boolean
  val strictScope: Boolean

  companion object {

    @JvmStatic
    @JvmOverloads
    fun create(
      queryExecutor: PolySymbolsQueryExecutor,
      virtualSymbols: Boolean = true
    ): PolySymbolsNameMatchQueryParams =
      create(queryExecutor, virtualSymbols, false, false)

    @JvmStatic
    fun create(
      queryExecutor: PolySymbolsQueryExecutor,
      virtualSymbols: Boolean = true,
      abstractSymbols: Boolean = false,
      strictScope: Boolean = false
    ): PolySymbolsNameMatchQueryParams =
      PolySymbolsNameMatchQueryParamsData(queryExecutor, virtualSymbols, abstractSymbols, strictScope)

  }
}

sealed interface PolySymbolsCodeCompletionQueryParams : PolySymbolsQueryParams {
  override val queryExecutor: PolySymbolsQueryExecutor

  /** Position to complete at in the last segment of the path **/
  val position: Int

  override val virtualSymbols: Boolean

  companion object {

    @JvmStatic
    @JvmOverloads
    fun create(
      queryExecutor: PolySymbolsQueryExecutor,
      position: Int,
      virtualSymbols: Boolean = true
    ): PolySymbolsCodeCompletionQueryParams =
      PolySymbolsCodeCompletionQueryParamsData(queryExecutor, position, virtualSymbols)

  }
}

private data class PolySymbolsCodeCompletionQueryParamsData(
  override val queryExecutor: PolySymbolsQueryExecutor,
  override val position: Int,
  override val virtualSymbols: Boolean,
) : PolySymbolsCodeCompletionQueryParams

private data class PolySymbolsListSymbolsQueryParamsData(
  override val queryExecutor: PolySymbolsQueryExecutor,
  override val expandPatterns: Boolean,
  override val virtualSymbols: Boolean,
  override val abstractSymbols: Boolean,
  override val strictScope: Boolean,
) : PolySymbolsListSymbolsQueryParams

private data class PolySymbolsNameMatchQueryParamsData(
  override val queryExecutor: PolySymbolsQueryExecutor,
  override val virtualSymbols: Boolean,
  override val abstractSymbols: Boolean,
  override val strictScope: Boolean,
) : PolySymbolsNameMatchQueryParams