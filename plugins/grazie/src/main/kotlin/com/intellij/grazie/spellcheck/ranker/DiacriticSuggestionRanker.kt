// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.grazie.spellcheck.ranker

import ai.grazie.spell.suggestion.ranker.AsciiRanker
import ai.grazie.spell.suggestion.ranker.SuggestionRanker
import ai.grazie.utils.LinkedSet

class DiacriticSuggestionRanker(
  private val fallbackSuggestionRanker: SuggestionRanker,
) : SuggestionRanker {
  override fun score(word: String, suggestions: LinkedSet<String>): Map<String, Double> {
    val weights = AsciiRanker().score(word, suggestions)
    if (weights.filter { it.value > 0 }.isEmpty()) {
      return fallbackSuggestionRanker.score(word, suggestions)
    }
    return weights
  }
}