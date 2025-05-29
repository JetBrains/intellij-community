// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns.impl

import com.intellij.util.containers.Stack
import com.intellij.webSymbols.PolySymbol
import com.intellij.webSymbols.PolySymbolNameSegment
import com.intellij.webSymbols.PolySymbolsScope
import com.intellij.webSymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.webSymbols.patterns.PolySymbolsPattern
import com.intellij.webSymbols.patterns.PolySymbolsPatternSymbolsResolver

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