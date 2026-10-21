// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.utils

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.polySymbol

/**
 * Factory for utility [PolySymbol]s that reference symbols from other
 * namespaces or kinds.
 */
object ReferencingPolySymbol {

  @JvmStatic
  @JvmOverloads
  fun create(
    kind: PolySymbolKind,
    name: String,
    vararg kinds: PolySymbolKind,
    priority: PolySymbol.Priority? = null,
    location: List<PolySymbolQualifiedName> = emptyList(),
  ): PolySymbol =
    polySymbol(kind = kind, name = name) {
      priority(priority)
      pattern {
        group {
          priority(priority)
          symbols { kinds.forEach { from(kind = it, location = location) } }
          symbolReference(name)
        }
      }
    }
}
