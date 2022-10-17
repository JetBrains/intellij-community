package com.intellij.cce.actions

import com.intellij.cce.core.*

class CompletionGolfEmulation(private val settings: Settings = Settings(), private val expectedLine: String) {
  fun pickBestSuggestion(currentLine: String, lookup: Lookup, session: Session): Lookup {
    val suggestions = lookup.suggestions.ofSource(settings.source).take(settings.topN).toMutableList()
    val normalizedExpectedLine = expectedLine.drop(currentLine.length)

    val line = checkForPerfectLine(normalizedExpectedLine, suggestions, lookup.prefix)
    val token = checkForFirstToken(normalizedExpectedLine, suggestions, lookup.prefix)

    val new = when {
      settings.checkLine && line != null -> {
        if (line.first.length > expectedLine.length / 2) {
          session.success = true
        }
        suggestions[line.second] = suggestions[line.second].withSuggestionKind(SuggestionKind.LINE)
        line
      }
      settings.checkToken && token != null -> {
        suggestions[token.second] = suggestions[token.second].withSuggestionKind(SuggestionKind.TOKEN)
        token
      }
      else -> Pair(expectedLine[currentLine.length].toString(), -1)
    }
    return Lookup(lookup.prefix, suggestions, lookup.latency, selectedPosition = new.second, isNew = lookup.isNew)
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
    var res: Pair<String, Int>? = null
    val expectedToken = firstToken(expectedLine)

    if (expectedToken.isEmpty()) {
      return null
    }

    suggestions.forEachIndexed { index, suggestion ->
      val suggestionToken = firstToken(suggestion.text.drop(prefix.length))

      findResult(suggestionToken, expectedToken, index, res)?.let { res = it }
    }

    return res
  }

  private fun findResult(suggestion: String, expected: String, index: Int, res: Pair<String, Int>?): Pair<String, Int>? {
    if (suggestion.isEmpty() || !expected.startsWith(suggestion)) {
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
   * @param checkToken In case first token in suggestion equals to first token in expected string, we can pick only first token from suggestion.

  If completion suggest only one token - this option is useless (see checkLine ↑). Suitable for full line or multiple token completions
   * @param source Take suggestions, with specific source
   *  - STANDARD  - standard non-full line completion
   *  - CODOTA    - <a href="https://plugins.jetbrains.com/plugin/7638-codota">https://plugins.jetbrains.com/plugin/7638-codota</a>
   *  - TAB_NINE  - <a href="https://github.com/codota/tabnine-intellij">https://github.com/codota/tabnine-intellij</a>
   *  - INTELLIJ  - <a href="https://jetbrains.team/p/ccrm/code/fl-inference">https://jetbrains.team/p/ccrm/code/fl-inference</a>
   * @param topN Take only N top suggestions, applying after filtering by source
   */
  data class Settings(
    val checkLine: Boolean = true,

    val checkToken: Boolean = true,
    val source: SuggestionSource? = null,
    var topN: Int = -1
  )

  companion object {
    fun createFromSettings(settings: Settings?, expectedLine: String): CompletionGolfEmulation {
      return CompletionGolfEmulation(settings ?: Settings(), expectedLine)
    }
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

