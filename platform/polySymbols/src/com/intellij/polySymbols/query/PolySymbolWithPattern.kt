// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.patterns.PolySymbolPattern

interface PolySymbolWithPattern : PolySymbol {

  /**
   * The pattern to match names against. As a result of pattern matching a [PolySymbolMatch] will be created.
   * A pattern may specify that a reference to other Poly Symbols is expected in some part of it.
   * For such places, appropriate segments with referenced Poly Symbols will be created and navigation,
   * validation and refactoring support is available out-of-the-box.
   */
  val pattern: PolySymbolPattern

}