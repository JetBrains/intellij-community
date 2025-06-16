// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns.impl

import com.intellij.polySymbols.query.PolySymbolCodeCompletionQueryParams
import com.intellij.polySymbols.query.PolySymbolQueryExecutor

internal class CompletionParameters(
  name: String,
  queryExecutor: PolySymbolQueryExecutor,
  val position: Int,
) : MatchParameters(name, queryExecutor) {
  constructor(name: String, params: PolySymbolCodeCompletionQueryParams)
    : this(name, params.queryExecutor, params.position)

  override fun toString(): String =
    "complete: $name (position: $position, framework: $framework)"

  fun withPosition(position: Int): CompletionParameters =
    CompletionParameters(name, queryExecutor, position)
}