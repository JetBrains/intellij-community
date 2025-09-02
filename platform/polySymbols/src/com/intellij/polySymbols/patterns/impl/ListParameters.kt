// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns.impl

import com.intellij.polySymbols.query.PolySymbolListSymbolsQueryParams
import com.intellij.polySymbols.query.PolySymbolQueryExecutor

internal open class ListParameters(
  val queryExecutor: PolySymbolQueryExecutor,
  val expandPatterns: Boolean,
) {

  constructor(params: PolySymbolListSymbolsQueryParams)
    : this(params.queryExecutor, params.expandPatterns)

  val framework: String? get() = queryExecutor.framework

  override fun toString(): String =
    "list (framework: $framework)"
}