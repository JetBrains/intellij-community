// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.golf

import com.intellij.cce.core.*

class CompletionGolfEmulation(private val strategy: CompletionGolfStrategy, private val expectedLine: String) {
  fun pickBestSuggestion(currentLine: String, lookup: Lookup, session: Session): Lookup {
    val suggestions = lookup.suggestions.ofSource(strategy.source)
      .let { if (strategy.topN > 0) it.take(strategy.topN) else it }
      .toMutableList()
    val normalizedExpectedLine = expectedLine.drop(currentLine.length)

    val line = checkForPerfectLine(normalizedExpectedLine, suggestions, lookup.prefix)
    val token = checkForFirstToken(normalizedExpectedLine, suggestions, lookup.prefix)

    val selectedPosition = when {
      strategy.checkLine && line != null -> {
        suggestions[line.second] = suggestions[line.second].withSuggestionKind(SuggestionKind.LINE)
        line.second
      }
      strategy.checkToken && token != null -> {
        suggestions[token.second] = suggestions[token.second].withSuggestionKind(SuggestionKind.TOKEN)
        token.second
      }
      else -> -1
    }
    return Lookup(lookup.prefix, currentLine.length, suggestions, lookup.latency, selectedPosition = selectedPosition, isNew = lookup.isNew)
  }

  private fun checkForPerfectLine(expectedLine: String, suggestions: List<Suggestion>, prefix: String): Pair<String, Int>? {
    var res: Pair<String, Int>? = null
    suggestions.forEachIndexed { index, suggestion ->
      val normalizedSuggestion = suggestion.text.drop(prefix.length)

      findResult(normalizedSuggestion, expectedLine, index, res)?.let { res = it }
    }

    return res
  }

  private fun checkForFirstToken(expectedLine: String, suggestions: List<Suggestion>, prefix: String): Pair<String, Int>? {
    val expectedToken = firstToken(expectedLine)

    if (expectedToken.isEmpty()) {
      return null
    }

    suggestions.forEachIndexed { index, suggestion ->
      val suggestionToken = firstToken(suggestion.text.drop(prefix.length))

      if (suggestionToken == expectedToken) {
        return suggestionToken to index
      }
    }

    return null
  }

  private fun findResult(suggestion: String, expected: String, index: Int, res: Pair<String, Int>?): Pair<String, Int>? {
    if (suggestion.isEmpty() || !expected.startsWith(suggestion)) {
      return null
    }
    val firstExpectedToken = firstToken(expected)
    if (firstExpectedToken.isNotEmpty() && suggestion.length < firstExpectedToken.length) {
      return null
    }

    val possibleResult = suggestion to index

    return if (res != null) {
      possibleResult.takeIf { res.first.length < suggestion.length }
    }
    else {
      possibleResult
    }
  }

  companion object {
    fun createFromStrategy(strategy: CompletionGolfStrategy, expectedLine: String): CompletionGolfEmulation {
      return CompletionGolfEmulation(strategy, expectedLine)
    }
  }
}

private fun List<Suggestion>.ofSource(source: SuggestionSource?): List<Suggestion> {
  return filter { source == null || it.source == source }
}

fun firstToken(line: String): String {
  var firstToken = ""
  for (ch in line) {
    if (ch.isLetter() || ch == '_' || ch.isDigit() && firstToken.isNotBlank()) firstToken += ch
    else break
  }
  return firstToken
}

