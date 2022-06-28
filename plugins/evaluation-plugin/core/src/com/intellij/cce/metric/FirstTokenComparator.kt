package com.intellij.cce.metric

import com.intellij.cce.core.Suggestion

class FirstTokenComparator : SuggestionsComparator {
  override fun accept(suggestion: Suggestion, expected: String): Boolean {
    var firstToken = ""
    for (ch in suggestion.text.trim()) {
      if (ch.isLetter() || ch == '_' || ch.isDigit() && firstToken.isNotBlank()) firstToken += ch
      else break
    }
    return firstToken == expected
  }
}
