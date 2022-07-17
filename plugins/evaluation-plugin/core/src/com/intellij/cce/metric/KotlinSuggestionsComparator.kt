package com.intellij.cce.metric

import com.intellij.cce.core.Suggestion

class KotlinSuggestionsComparator : SuggestionsComparator {
  override fun accept(suggestion: Suggestion, expected: String): Boolean {
    return when {
      suggestion.text == expected -> true
      suggestion.text.split(" =").first() == expected -> true
      else -> false
    }
  }
}