// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.utils

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.polySymbol

/**
 * Factory for utility [PolySymbol]s that map from one symbol to another.
 *
 * The mapping will be made when the created symbol's name matches exactly the
 * queried name. As a result, a reference to the symbol resolved through the
 * `mappingPath` will be created.
 */
object MappedPolySymbol {

  @JvmOverloads
  @JvmStatic
  fun create(
    kind: PolySymbolKind,
    name: String,
    vararg mappingPath: PolySymbolQualifiedName,
    priority: PolySymbol.Priority? = null,
  ): PolySymbol = polySymbol(kind = kind, name = name) {
    priority(priority)
    pattern { symbolReference(mappingPath.toList()) }
  }
}
