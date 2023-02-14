// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns.impl

import com.intellij.util.containers.Stack
import com.intellij.util.text.CharSequenceSubSequence
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.WebSymbolNameSegment
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.webSymbols.patterns.WebSymbolsPattern
import com.intellij.webSymbols.patterns.WebSymbolsPatternSymbolsResolver
import java.util.regex.Pattern

internal class RegExpPattern(private val regex: String, private val caseSensitive: Boolean = false) : WebSymbolsPattern() {
  private val pattern: Pattern by lazy(LazyThreadSafetyMode.NONE) {
    if (caseSensitive)
      Pattern.compile(regex)
    else
      Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
  }

  override fun getStaticPrefixes(): Sequence<String> = sequenceOf(getPatternCompletablePrefix(regex))

  override fun isStaticAndRequired(): Boolean = false

  override fun match(owner: WebSymbol?,
                     scopeStack: Stack<WebSymbolsScope>,
                     symbolsResolver: WebSymbolsPatternSymbolsResolver?,
                     params: MatchParameters,
                     start: Int,
                     end: Int): List<MatchResult> {
    val matcher = pattern.matcher(CharSequenceSubSequence(params.name, start, end))
    return if (matcher.find(0) && matcher.start() == 0)
      listOf(MatchResult(WebSymbolNameSegment(start, start + matcher.end(),
                                               owner?.let { listOf(it) } ?: emptyList(),
                                              matchScore = getPatternCompletablePrefix(regex).length)))
    else emptyList()
  }

  override fun getCompletionResults(owner: WebSymbol?,
                                    scopeStack: Stack<WebSymbolsScope>,
                                    symbolsResolver: WebSymbolsPatternSymbolsResolver?,
                                    params: CompletionParameters,
                                    start: Int,
                                    end: Int): CompletionResults =
    getPatternCompletablePrefix(regex)
      .takeIf { it.isNotBlank() }
      ?.let {
        CompletionResults(WebSymbolCodeCompletionItem.create(it, start, true, displayName = "$it…", symbol = owner))
      }
    ?: CompletionResults(WebSymbolCodeCompletionItem.create("", start, true, displayName = "…", symbol = owner))


  override fun toString(): String =
    "\\${regex}\\"
}