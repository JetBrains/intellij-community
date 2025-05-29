// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns.impl

import com.intellij.util.containers.Stack
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolNameSegment
import com.intellij.polySymbols.PolySymbolsScope
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.patterns.PolySymbolsPattern
import com.intellij.polySymbols.patterns.PolySymbolsPatternSymbolsResolver

internal class StaticPattern(val content: String) : PolySymbolsPattern() {
  override fun getStaticPrefixes(): Sequence<String> = sequenceOf(content)

  override fun match(owner: PolySymbol?,
                     scopeStack: Stack<PolySymbolsScope>,
                     symbolsResolver: PolySymbolsPatternSymbolsResolver?,
                     params: MatchParameters,
                     start: Int,
                     end: Int): List<MatchResult> =
    if (content.length <= end - start && params.name.startsWith(content, start))
      listOf(MatchResult(PolySymbolNameSegment.create(start, start + content.length)))
    else emptyList()

  override fun list(owner: PolySymbol?,
                    scopeStack: Stack<PolySymbolsScope>,
                    symbolsResolver: PolySymbolsPatternSymbolsResolver?,
                    params: ListParameters): List<ListResult> =
    listOf(ListResult(content, PolySymbolNameSegment.create(0, content.length)))

  override fun complete(owner: PolySymbol?,
                        scopeStack: Stack<PolySymbolsScope>,
                        symbolsResolver: PolySymbolsPatternSymbolsResolver?,
                        params: CompletionParameters,
                        start: Int,
                        end: Int): CompletionResults =
    CompletionResults(PolySymbolCodeCompletionItem.create(content, start))

  override fun toString(): String =
    "\"${content}\""
}