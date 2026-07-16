// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.references.impl

import com.intellij.openapi.util.TextRange
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolNameSegment
import com.intellij.polySymbols.PolySymbolNameSegment.MatchProblem
import com.intellij.polySymbols.query.PolySymbolMatch
import com.intellij.polySymbols.query.PolySymbolQueryExecutorFactory
import com.intellij.polySymbols.references.PolySymbolOwnReferencesBuilder
import com.intellij.polySymbols.references.PolySymbolReference
import com.intellij.polySymbols.references.PolySymbolReferenceProblem
import com.intellij.polySymbols.utils.asSingleSymbol
import com.intellij.polySymbols.utils.nameSegments
import com.intellij.polySymbols.utils.unwrapMatchedSymbols
import com.intellij.psi.PsiElement
import com.intellij.util.SmartList

internal class PolySymbolOwnReferencesBuilderImpl(private val element: PsiElement) : PolySymbolOwnReferencesBuilder {

  private val references = SmartList<PolySymbolReference>()

  override fun resolveFromNameMatchQuery(
    kind: PolySymbolKind,
    name: String,
  ) =
    resolveFromNameMatchQuery(kind, name, TextRange(0, name.length))

  override fun resolveFromNameMatchQuery(kind: PolySymbolKind, name: String, textRangeInElement: TextRange) =
    reference(textRangeInElement, kind) {
      PolySymbolQueryExecutorFactory.create(element, true)
        .nameMatchQuery(kind, name)
        .run()
    }

  override fun resolveFromNameMatchQuery(
    kind: PolySymbolKind,
    name: String,
    filter: (PolySymbol) -> Boolean,
  ) = resolveFromNameMatchQuery(kind, name, TextRange(0, name.length), filter)

  override fun resolveFromNameMatchQuery(
    kind: PolySymbolKind,
    name: String,
    textRangeInElement: TextRange,
    filter: (PolySymbol) -> Boolean,
  ) = reference(TextRange(0, name.length), kind) {
    PolySymbolQueryExecutorFactory.create(element, true)
      .nameMatchQuery(kind, name)
      .run()
      .filter(filter)
  }

  override fun reference(
    textRangeInElement: TextRange,
    kind: PolySymbolKind,
    resolver: () -> List<PolySymbol>,
  ) {
    references.add(PolySymbolOwnReference(element, textRangeInElement, kind, resolver))
  }

  fun build(): List<PolySymbolReference> =
    references

  private class PolySymbolOwnReference(
    private val psiElement: PsiElement,
    private val textRangeInElement: TextRange,
    private val kind: PolySymbolKind,
    private val resolver: () -> List<PolySymbol>,
  ) : PolySymbolReference {

    private val resolvedSymbols by lazy {
      val text = textRangeInElement.substring(psiElement.text)
      resolver().flatMap { symbol ->
        assert(symbol.name == text) {
          "Symbol name ${symbol.name} does not match text range contents $text: $symbol"
        }
        // Filter out complex symbols in own references.
        // Overall, only simple symbol kind references are allowed on own references.
        symbol.unwrapMatchedSymbols()
          .filter { it.name == text }
      }
    }

    override fun resolveReference(): Collection<PolySymbol> =
      resolvedSymbols

    override fun getElement(): PsiElement =
      psiElement

    override fun getRangeInElement(): TextRange =
      textRangeInElement

    override fun getProblems(): Collection<PolySymbolReferenceProblem> {
      val symbol =
        resolvedSymbols.toList().asSingleSymbol()
        ?: PolySymbolMatch.create(
          textRangeInElement.substring(psiElement.text),
          kind,
          PolySymbolNameSegment.create(
            0, textRangeInElement.length,
            problem = MatchProblem.UNKNOWN_SYMBOL,
            symbolKinds = setOf(kind),
          )
        )
      return PolySymbolReferenceProblemProvider.getProblems(
        psiElement,
        symbol,
        textRangeInElement,
        symbol.nameSegments,
        0,
        symbol.apiStatus
      )
    }

  }

}