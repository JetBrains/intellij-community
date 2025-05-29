// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns.impl

import com.intellij.polySymbols.query.WebSymbolsNameMatchQueryParams
import com.intellij.polySymbols.query.PolySymbolsQueryExecutor

internal open class MatchParameters(val name: String,
                                    val queryExecutor: PolySymbolsQueryExecutor) {

  constructor(name: String, params: WebSymbolsNameMatchQueryParams)
    : this(name, params.queryExecutor)

  val framework: String? get() = queryExecutor.framework

  override fun toString(): String =
    "match: $name (framework: $framework)"
}