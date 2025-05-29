// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.webSymbols.PolySymbol
import com.intellij.webSymbols.PolySymbolNameSegment

interface PolySymbolMatchBuilder {
  fun addNameSegments(value: List<PolySymbolNameSegment>): PolySymbolMatchBuilder
  fun addNameSegments(vararg value: PolySymbolNameSegment): PolySymbolMatchBuilder
  fun addNameSegment(value: PolySymbolNameSegment): PolySymbolMatchBuilder
  fun explicitPriority(value: PolySymbol.Priority): PolySymbolMatchBuilder
  fun explicitProximity(value: Int): PolySymbolMatchBuilder
  fun setProperty(name: String, value: Any): PolySymbolMatchBuilder
}