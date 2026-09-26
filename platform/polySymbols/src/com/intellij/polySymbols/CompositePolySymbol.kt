// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols

import com.intellij.polySymbols.query.PolySymbolMatch

/**
 * A symbol, which name consists of other Poly Symbols.
 *
 * @see [PolySymbolMatch]
 */
interface CompositePolySymbol : PolySymbol {

  /**
   * List of [PolySymbolNameSegment]. Each segment describes a range in the symbol name.
   * Segments can be built of other Poly Symbols and/or have related matching problems - missing required part,
   * unknown symbol name or be a duplicate of another segment.
   *
   * See [Model Queries - Example](https://plugins.jetbrains.com/docs/intellij/websymbols-implementation.html#example) for an example.
   */
  val nameSegments: List<PolySymbolNameSegment>

}