// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns.impl

import com.intellij.util.containers.Stack
import com.intellij.polySymbols.PolySymbolsScope
import com.intellij.polySymbols.patterns.ComplexPatternOptions
import com.intellij.polySymbols.patterns.PolySymbolsPattern
import com.intellij.polySymbols.query.PolySymbolsQueryExecutor

internal interface ComplexPatternConfigProvider {

  fun getPatterns(): List<PolySymbolsPattern>

  fun getOptions(queryExecutor: PolySymbolsQueryExecutor, scopeStack: Stack<PolySymbolsScope>): ComplexPatternOptions

  val isStaticAndRequired: Boolean

}