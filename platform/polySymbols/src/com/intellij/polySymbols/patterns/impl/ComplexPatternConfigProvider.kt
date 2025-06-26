// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns.impl

import com.intellij.polySymbols.patterns.ComplexPatternOptions
import com.intellij.polySymbols.patterns.PolySymbolPattern
import com.intellij.polySymbols.query.PolySymbolQueryExecutor
import com.intellij.polySymbols.query.PolySymbolQueryStack

internal interface ComplexPatternConfigProvider {

  fun getPatterns(): List<PolySymbolPattern>

  fun getOptions(queryExecutor: PolySymbolQueryExecutor, stack: PolySymbolQueryStack): ComplexPatternOptions

  val isStaticAndRequired: Boolean

}