// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns.impl

import com.intellij.polySymbols.query.PolySymbolNameMatchQueryParams
import com.intellij.polySymbols.query.PolySymbolQueryExecutor

internal open class MatchParameters(
  val name: String,
  val queryExecutor: PolySymbolQueryExecutor,
) {

  constructor(name: String, params: PolySymbolNameMatchQueryParams)
    : this(name, params.queryExecutor)

  val framework: String? get() = queryExecutor.framework

  override fun toString(): String =
    "match: $name (framework: $framework)"
}