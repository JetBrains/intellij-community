package com.intellij.cce.core

data class Suggestion(
  val text: String,
  val presentationText: String,
  val source: SuggestionSource,
  val score: Double? = null,
  val kind: SuggestionKind = SuggestionKind.ANY
) {
  fun withSuggestionKind(kind: SuggestionKind): Suggestion {
    return Suggestion(text, presentationText, source, score, kind)
  }
}
