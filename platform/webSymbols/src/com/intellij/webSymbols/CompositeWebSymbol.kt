// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.webSymbols.query.WebSymbolMatch

/**
 * A symbol, which name consists of other Web Symbols.
 *
 * @see [WebSymbolMatch]
 */
interface CompositeWebSymbol : WebSymbol {

  /**
   * List of [WebSymbolNameSegment]. Each segment describes a range in the symbol name.
   * Segments can be built of other Web Symbols and/or have related matching problems - missing required part,
   * unknown symbol name or be a duplicate of another segment.
   *
   * See [Model Queries - Example](https://plugins.jetbrains.com/docs/intellij/websymbols-implementation.html#example) for an example.
   */
  val nameSegments: List<WebSymbolNameSegment>

}