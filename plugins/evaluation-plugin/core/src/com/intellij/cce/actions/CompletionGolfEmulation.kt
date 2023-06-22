package com.intellij.cce.actions

import com.intellij.cce.core.*

class CompletionGolfEmulation(private val settings: Settings = Settings(), private val expectedLine: String) {
  fun pickBestSuggestion(currentLine: String, lookup: Lookup, session: Session): Lookup {
    val suggestions = lookup.suggestions.ofSource(settings.source)
      .let { if (settings.topN > 0) it.take(settings.topN) else it }
      .toMutableList()
    val normalizedExpectedLine = expectedLine.drop(currentLine.length)

    val line = checkForPerfectLine(normalizedExpectedLine, suggestions, lookup.prefix)
    val token = checkForFirstToken(normalizedExpectedLine, suggestions, lookup.prefix)

    val selectedPosition = when {
      settings.checkLine && line != null -> {
        if (line.first.length > expectedLine.trim().length / 2) {
          session.success = true
        }
        suggestions[line.second] = suggestions[line.second].withSuggestionKind(SuggestionKind.LINE)
        line.second
      }
      settings.checkToken && token != null -> {
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
    } else {
      possibleResult
    }
  }

  /**
   * @param checkLine Check if expected line starts with suggestion from completion
   * @param invokeOnEachChar Close popup after unsuccessful completion and invoke again
   * @param checkToken In case first token in suggestion equals to first token in expected string, we can pick only first token from suggestion.

  If completion suggest only one token - this option is useless (see checkLine ↑). Suitable for full line or multiple token completions
   * @param source Take suggestions, with specific source
   *  - STANDARD  - standard non-full line completion
   *  - CODOTA    - <a href="https://plugins.jetbrains.com/plugin/7638-codota">https://plugins.jetbrains.com/plugin/7638-codota</a>
   *  - TAB_NINE  - <a href="https://github.com/codota/tabnine-intellij">https://github.com/codota/tabnine-intellij</a>
   *  - INTELLIJ  - <a href="https://jetbrains.team/p/ccrm/code/fl-inference">https://jetbrains.team/p/ccrm/code/fl-inference</a>
   * @param topN Take only N top suggestions, applying after filtering by source
   * @param isBenchmark Call completion once for each token.
   * @param randomSeed Random seed for evaluation. Currently used to select token prefix in benchmark mode.
   * @param suggestionsProvider Name of provider of suggestions (use DEFAULT for IDE completion)
   */
  data class Settings(
    val checkLine: Boolean = true,
    val invokeOnEachChar: Boolean = false,

    val checkToken: Boolean = true,
    val source: SuggestionSource? = null,
    var topN: Int = -1,

    val isBenchmark: Boolean = false,
    val randomSeed: Int = 0,
    val suggestionsProvider: String = DEFAULT_PROVIDER
  ) {
    fun isDefaultProvider(): Boolean = suggestionsProvider == DEFAULT_PROVIDER
  }

  companion object {
    fun createFromSettings(settings: Settings?, expectedLine: String): CompletionGolfEmulation {
      return CompletionGolfEmulation(settings ?: Settings(), expectedLine)
    }

    private const val DEFAULT_PROVIDER: String = "DEFAULT"
  }
}

private fun List<Suggestion>.ofSource(source: SuggestionSource?): List<Suggestion> {
  return filter { source == null || it.source == source }
}

fun Lookup.selectedWithoutPrefix(): String? {
  if (selectedPosition == -1) return null

  return suggestions.getOrNull(selectedPosition)?.let {
    if (it.kind == SuggestionKind.TOKEN) firstToken(it.text) else it.text
  }?.drop(prefix.length)?.takeIf { it.isNotEmpty() }
}

fun firstToken(line: String): String {
  var firstToken = ""
  for (ch in line) {
    if (ch.isLetter() || ch == '_' || ch.isDigit() && firstToken.isNotBlank()) firstToken += ch
    else break
  }
  return firstToken
}

