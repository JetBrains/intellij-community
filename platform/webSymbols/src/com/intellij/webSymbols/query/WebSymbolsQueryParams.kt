// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

sealed interface WebSymbolsQueryParams {

  val framework: String?
    get() = queryExecutor.framework

  val queryExecutor: WebSymbolsQueryExecutor

  val virtualSymbols: Boolean
}

sealed interface WebSymbolsListSymbolsQueryParams : WebSymbolsQueryParams {
  override val queryExecutor: WebSymbolsQueryExecutor
  val expandPatterns: Boolean
  override val virtualSymbols: Boolean
  val abstractSymbols: Boolean
  val strictScope: Boolean

  companion object {

    @JvmStatic
    @JvmOverloads
    fun create(
      queryExecutor: WebSymbolsQueryExecutor,
      expandPatterns: Boolean,
      virtualSymbols: Boolean = true
    ): WebSymbolsListSymbolsQueryParams =
      create(queryExecutor, expandPatterns, virtualSymbols, false, false)

    @JvmStatic
    fun create(
      queryExecutor: WebSymbolsQueryExecutor,
      expandPatterns: Boolean,
      virtualSymbols: Boolean = true,
      abstractSymbols: Boolean = false,
      strictScope: Boolean = false,
    ): WebSymbolsListSymbolsQueryParams =
      WebSymbolsListSymbolsQueryParamsData(queryExecutor, expandPatterns, virtualSymbols, abstractSymbols, strictScope)

  }
}


sealed interface WebSymbolsNameMatchQueryParams : WebSymbolsQueryParams {
  override val queryExecutor: WebSymbolsQueryExecutor
  override val virtualSymbols: Boolean
  val abstractSymbols: Boolean
  val strictScope: Boolean

  companion object {

    @JvmStatic
    @JvmOverloads
    fun create(
      queryExecutor: WebSymbolsQueryExecutor,
      virtualSymbols: Boolean = true
    ): WebSymbolsNameMatchQueryParams =
      create(queryExecutor, virtualSymbols, false, false)

    @JvmStatic
    fun create(
      queryExecutor: WebSymbolsQueryExecutor,
      virtualSymbols: Boolean = true,
      abstractSymbols: Boolean = false,
      strictScope: Boolean = false
    ): WebSymbolsNameMatchQueryParams =
      WebSymbolsNameMatchQueryParamsData(queryExecutor, virtualSymbols, abstractSymbols, strictScope)

  }
}

sealed interface WebSymbolsCodeCompletionQueryParams : WebSymbolsQueryParams {
  override val queryExecutor: WebSymbolsQueryExecutor

  /** Position to complete at in the last segment of the path **/
  val position: Int

  override val virtualSymbols: Boolean

  companion object {

    @JvmStatic
    @JvmOverloads
    fun create(
      queryExecutor: WebSymbolsQueryExecutor,
      position: Int,
      virtualSymbols: Boolean = true
    ): WebSymbolsCodeCompletionQueryParams =
      WebSymbolsCodeCompletionQueryParamsData(queryExecutor, position, virtualSymbols)

  }
}

private data class WebSymbolsCodeCompletionQueryParamsData(
  override val queryExecutor: WebSymbolsQueryExecutor,
  override val position: Int,
  override val virtualSymbols: Boolean,
) : WebSymbolsCodeCompletionQueryParams

private data class WebSymbolsListSymbolsQueryParamsData(
  override val queryExecutor: WebSymbolsQueryExecutor,
  override val expandPatterns: Boolean,
  override val virtualSymbols: Boolean,
  override val abstractSymbols: Boolean,
  override val strictScope: Boolean,
) : WebSymbolsListSymbolsQueryParams

private data class WebSymbolsNameMatchQueryParamsData(
  override val queryExecutor: WebSymbolsQueryExecutor,
  override val virtualSymbols: Boolean,
  override val abstractSymbols: Boolean,
  override val strictScope: Boolean,
) : WebSymbolsNameMatchQueryParams