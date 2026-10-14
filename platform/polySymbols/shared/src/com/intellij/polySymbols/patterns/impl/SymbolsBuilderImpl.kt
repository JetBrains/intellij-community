// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns.impl

import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.patterns.ReferenceBuilder
import com.intellij.polySymbols.patterns.SymbolsBuilder

internal class SymbolsBuilderImpl : SymbolsBuilder {

  private val references: MutableList<PolySymbolPatternReferenceResolver.Reference> = mutableListOf()

  override fun from(
    kind: PolySymbolKind,
    location: List<PolySymbolQualifiedName>,
    body: ReferenceBuilder.() -> Unit,
  ) {
    val builder = ReferenceBuilderImpl().apply(body)
    references += PolySymbolPatternReferenceResolver.Reference(
      location = location,
      kind = kind,
      filter = builder.filterValue,
      excludeModifiers = builder.excludeModifiersValue,
      nameConversionRules = builder.nameConversionRules,
    )
  }

  internal fun buildResolver(): PolySymbolPatternReferenceResolver? {
    if (references.isEmpty()) return null
    return PolySymbolPatternReferenceResolver(*references.toTypedArray())
  }
}
