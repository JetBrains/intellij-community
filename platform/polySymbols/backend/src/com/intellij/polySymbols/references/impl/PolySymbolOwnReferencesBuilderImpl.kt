// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.references.impl

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.query.PolySymbolQueryExecutorFactory
import com.intellij.polySymbols.references.PolySymbolOwnReferences
import com.intellij.polySymbols.references.PolySymbolReference
import com.intellij.psi.PsiElement
import com.intellij.util.SmartList

internal class PolySymbolOwnReferencesBuilderImpl(private val element: PsiElement) : PolySymbolOwnReferences.Builder {

  private val referencedSymbols = LinkedHashMap<Int, PolySymbol>()
  private val references = SmartList<PolySymbolReference>()

  override fun fromNameMatchQuery(kind: PolySymbolKind, name: String) =
    fromNameMatchQuery(kind, name) { true }

  override fun fromNameMatchQuery(
    kind: PolySymbolKind,
    name: String,
    filter: (PolySymbol) -> Boolean,
  ) {
    PolySymbolQueryExecutorFactory.create(element, true)
      .nameMatchQuery(kind, name)
      .run()
      .filter(filter)
      .forEach { reference(it) }
  }

  override fun reference(symbol: PolySymbol) =
    reference(symbol, 0)

  override fun reference(symbol: PolySymbol, offset: Int, showProblems: Boolean) {
    referencedSymbols[offset] = symbol
    references.addAll(createPolySymbolReferences(element, offset, symbol, showProblems))
  }

  override fun references(offsetsToSymbols: Map<Int, PolySymbol>, showProblems: Boolean) {
    offsetsToSymbols.forEach { (offset, symbol) -> reference(symbol, offset, showProblems) }
  }

  fun build(): PolySymbolOwnReferences =
    PolySymbolOwnReferences(referencedSymbols, references)

}