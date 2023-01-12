// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

data class WebSymbolsCodeCompletionQueryParams(
  override val queryExecutor: WebSymbolsQueryExecutor,
  /** Position to complete at in the last segment of the path **/
  val position: Int,
  override val virtualSymbols: Boolean = true,
) : WebSymbolsQueryParams