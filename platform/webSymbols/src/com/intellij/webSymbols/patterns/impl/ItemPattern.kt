// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns.impl

import com.intellij.util.containers.Stack
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.query.WebSymbolMatch
import com.intellij.webSymbols.WebSymbolNameSegment
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.webSymbols.patterns.WebSymbolsPattern
import com.intellij.webSymbols.patterns.WebSymbolsPatternItemsProvider
import kotlin.math.max

internal class ItemPattern(val displayName: String?) : WebSymbolsPattern() {
  override fun getStaticPrefixes(): Sequence<String> = sequenceOf("")

  override fun isStaticAndRequired(): Boolean = false

  override fun match(owner: WebSymbol?,
                     scopeStack: Stack<WebSymbolsScope>,
                     itemsProvider: WebSymbolsPatternItemsProvider?,
                     params: MatchParameters,
                     start: Int,
                     end: Int): List<MatchResult> {
    if (start == end) {
      // TODO should be "missing required part", but needs improvements in sequence pattern code completion
      return listOf(MatchResult(listOf(WebSymbolNameSegment(
        start, end, emptyList(),
        problem = WebSymbolNameSegment.MatchProblem.UNKNOWN_ITEM,
        displayName = displayName,
        symbolKinds = itemsProvider?.getSymbolKinds(owner ?: scopeStack.lastOrNull() as? WebSymbol) ?: emptySet()
      ))))
    }

    val hits = itemsProvider
                 ?.matchName(params.name.substring(start, end), scopeStack, params.queryExecutor)
               ?: emptyList()

    return listOf(MatchResult(
      when {
        hits.size == 1 && hits[0] is WebSymbolMatch ->
          hits[0].nameSegments.map { it.withOffset(start) }

        hits.isNotEmpty() ->
          listOf(WebSymbolNameSegment(
            start, end, hits, displayName = displayName
          ))

        else -> listOf(WebSymbolNameSegment(
          start, end,
          emptyList(),
          problem = WebSymbolNameSegment.MatchProblem.UNKNOWN_ITEM,
          displayName = displayName,
          symbolKinds = itemsProvider?.getSymbolKinds(owner ?: scopeStack.lastOrNull() as? WebSymbol) ?: emptySet(),
        ))
      }
    ))
  }

  override fun getCompletionResults(owner: WebSymbol?,
                                    scopeStack: Stack<WebSymbolsScope>,
                                    itemsProvider: WebSymbolsPatternItemsProvider?,
                                    params: CompletionParameters,
                                    start: Int,
                                    end: Int): CompletionResults =
    itemsProvider
      ?.codeCompletion(params.name.substring(start, end), max(params.position - start, 0), scopeStack, params.queryExecutor)
      ?.let { results ->
        val stop = start == end && start == params.position
        CompletionResults(results.map { it.withOffset(it.offset + start).withStopSequencePatternEvaluation(stop) }, true)
      }
    ?: CompletionResults(emptyList(), true)


  override fun toString(): String = "{item}"

}
