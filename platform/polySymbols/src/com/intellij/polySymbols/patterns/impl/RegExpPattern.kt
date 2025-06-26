// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns.impl

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolNameSegment
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.patterns.PolySymbolPattern
import com.intellij.polySymbols.patterns.PolySymbolPatternSymbolsResolver
import com.intellij.polySymbols.query.PolySymbolQueryStack
import com.intellij.util.text.CharSequenceSubSequence
import java.util.regex.Pattern

internal class RegExpPattern(private val regex: String, private val caseSensitive: Boolean = false) : PolySymbolPattern() {
  private val pattern: Pattern by lazy(LazyThreadSafetyMode.NONE) {
    if (caseSensitive)
      Pattern.compile(regex)
    else
      Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
  }

  override fun getStaticPrefixes(): Sequence<String> = sequenceOf(getPatternCompletablePrefix(regex))

  override fun isStaticAndRequired(): Boolean = false

  override fun match(
    owner: PolySymbol?,
    stack: PolySymbolQueryStack,
    symbolsResolver: PolySymbolPatternSymbolsResolver?,
    params: MatchParameters,
    start: Int,
    end: Int,
  ): List<MatchResult> {
    val matcher = pattern.matcher(CharSequenceSubSequence(params.name, start, end))
    return if (matcher.find(0) && matcher.start() == 0)
      listOf(MatchResult(PolySymbolNameSegment.create(
        start, start + matcher.end(),
        owner?.let { listOf(it) } ?: emptyList(),
        matchScore = getPatternCompletablePrefix(regex).length
      )))
    else emptyList()
  }

  override fun list(
    owner: PolySymbol?,
    stack: PolySymbolQueryStack,
    symbolsResolver: PolySymbolPatternSymbolsResolver?,
    params: ListParameters,
  ): List<ListResult> =
    emptyList()

  override fun complete(
    owner: PolySymbol?,
    stack: PolySymbolQueryStack,
    symbolsResolver: PolySymbolPatternSymbolsResolver?,
    params: CompletionParameters,
    start: Int,
    end: Int,
  ): CompletionResults =
    getPatternCompletablePrefix(regex)
      .takeIf { it.isNotBlank() }
      ?.let {
        CompletionResults(
          PolySymbolCodeCompletionItem.builder(it, start, owner)
            .completeAfterInsert(true)
            .displayName("$it…")
            .build()
        )
      }
    ?: CompletionResults(
      PolySymbolCodeCompletionItem.builder("", start, owner)
        .completeAfterInsert(true)
        .displayName("…")
        .build()
    )


  override fun toString(): String =
    "\\${regex}\\"
}