// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns.impl

import com.intellij.webSymbols.query.WebSymbolsListSymbolsQueryParams
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor

internal open class ListParameters(
  val queryExecutor: WebSymbolsQueryExecutor,
  val expandPatterns: Boolean,
) {

  constructor(params: WebSymbolsListSymbolsQueryParams)
    : this(params.queryExecutor, params.expandPatterns)

  val framework: String? get() = queryExecutor.framework

  override fun toString(): String =
    "list (framework: $framework)"
}