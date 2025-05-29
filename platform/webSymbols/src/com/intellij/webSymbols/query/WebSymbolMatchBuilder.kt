// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.webSymbols.PolySymbol
import com.intellij.webSymbols.WebSymbolNameSegment

interface WebSymbolMatchBuilder {
  fun addNameSegments(value: List<WebSymbolNameSegment>): WebSymbolMatchBuilder
  fun addNameSegments(vararg value: WebSymbolNameSegment): WebSymbolMatchBuilder
  fun addNameSegment(value: WebSymbolNameSegment): WebSymbolMatchBuilder
  fun explicitPriority(value: PolySymbol.Priority): WebSymbolMatchBuilder
  fun explicitProximity(value: Int): WebSymbolMatchBuilder
  fun setProperty(name: String, value: Any): WebSymbolMatchBuilder
}