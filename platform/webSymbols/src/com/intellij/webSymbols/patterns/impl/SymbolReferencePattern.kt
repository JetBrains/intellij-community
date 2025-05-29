// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns.impl

import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.applyIf
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.PolySymbol
import com.intellij.webSymbols.PolySymbolNameSegment
import com.intellij.webSymbols.PolySymbolsScope
import com.intellij.webSymbols.impl.selectBest
import com.intellij.webSymbols.impl.withDisplayName
import com.intellij.webSymbols.impl.withOffset
import com.intellij.webSymbols.patterns.PolySymbolsPattern
import com.intellij.webSymbols.patterns.PolySymbolsPatternSymbolsResolver
import com.intellij.webSymbols.query.PolySymbolMatch
import com.intellij.webSymbols.utils.lastPolySymbol
import com.intellij.webSymbols.utils.nameSegments
import kotlin.math.max

internal class SymbolReferencePattern(val displayName: String?) : PolySymbolsPattern() {
  override fun getStaticPrefixes(): Sequence<String> = sequenceOf("")

  override fun isStaticAndRequired(): Boolean = false

  override fun match(owner: PolySymbol?,
                     scopeStack: Stack<PolySymbolsScope>,
                     symbolsResolver: PolySymbolsPatternSymbolsResolver?,
                     params: MatchParameters,
                     start: Int,
                     end: Int): List<MatchResult> {
    if (start == end) {
      // TODO should be "missing required part", but needs improvements in sequence pattern code completion
      return listOf(MatchResult(listOf(PolySymbolNameSegment.create(
        start, end, emptyList(),
        problem = PolySymbolNameSegment.MatchProblem.UNKNOWN_SYMBOL,
        displayName = displayName,
        symbolKinds = symbolsResolver?.getSymbolKinds(owner ?: scopeStack.lastPolySymbol) ?: emptySet()
      ))))
    }

    ProgressManager.checkCanceled()
    val hits = symbolsResolver
                 ?.matchName(params.name.substring(start, end), scopeStack, params.queryExecutor)
                 ?.selectBest(PolySymbol::nameSegments, PolySymbol::priority, PolySymbol::extension)
               ?: emptyList()

    return listOf(MatchResult(
      when {
        hits.size == 1 && hits[0] is PolySymbolMatch ->
          (hits[0] as PolySymbolMatch).nameSegments.map {
            it.withOffset(start).applyIf(it.start != it.end) { withDisplayName(this@SymbolReferencePattern.displayName) }
          }

        hits.isNotEmpty() ->
          listOf(PolySymbolNameSegment.create(
            start, end, hits, displayName = displayName
          ))

        else -> listOf(PolySymbolNameSegment.create(
          start, end,
          emptyList(),
          problem = PolySymbolNameSegment.MatchProblem.UNKNOWN_SYMBOL,
          displayName = displayName,
          symbolKinds = symbolsResolver?.getSymbolKinds(owner ?: scopeStack.lastPolySymbol) ?: emptySet(),
        ))
      }
    ))
  }

  override fun list(owner: PolySymbol?,
                    scopeStack: Stack<PolySymbolsScope>,
                    symbolsResolver: PolySymbolsPatternSymbolsResolver?,
                    params: ListParameters): List<ListResult> =
    symbolsResolver
      ?.listSymbols(scopeStack, params.queryExecutor, params.expandPatterns)
      ?.groupBy { it.name }
      ?.flatMap { (name, rawList) ->
        val list = rawList.selectBest(PolySymbol::nameSegments, PolySymbol::priority, PolySymbol::extension)
        when {
          list.size == 1 && list[0] is PolySymbolMatch ->
            (list[0] as PolySymbolMatch).let { match ->
              listOf(ListResult(match.name, match.nameSegments.map { it.withDisplayName(displayName) }))
            }

          list.isNotEmpty() ->
            listOf(ListResult(name, PolySymbolNameSegment.create(
              0, name.length, list, displayName = displayName
            )))

          else -> emptyList()
        }
      }
    ?: emptyList()

  override fun complete(owner: PolySymbol?,
                        scopeStack: Stack<PolySymbolsScope>,
                        symbolsResolver: PolySymbolsPatternSymbolsResolver?,
                        params: CompletionParameters,
                        start: Int,
                        end: Int): CompletionResults =
    symbolsResolver
      ?.codeCompletion(params.name.substring(start, end), max(params.position - start, 0), scopeStack, params.queryExecutor)
      ?.let { results ->
        val stop = start == end && start == params.position
        CompletionResults(results.map { it.withOffset(it.offset + start).withStopSequencePatternEvaluation(stop) }, true)
      }
    ?: CompletionResults(emptyList(), true)


  override fun toString(): String = "{item}"

}
