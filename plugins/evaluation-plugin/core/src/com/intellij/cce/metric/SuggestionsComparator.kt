package com.intellij.cce.metric

import com.intellij.cce.actions.CompletionType
import com.intellij.cce.core.Language
import com.intellij.cce.core.Suggestion

interface SuggestionsComparator {
  companion object {
    val DEFAULT = object : SuggestionsComparator {
      override fun accept(suggestion: Suggestion, expected: String): Boolean = suggestion.text == expected
    }

    fun create(language: Language, completionType: CompletionType): SuggestionsComparator {
      return when {
        language == Language.KOTLIN -> KotlinSuggestionsComparator()
        language == Language.CPP -> FirstTokenComparator()
        completionType == CompletionType.FULL_LINE -> FirstTokenComparator()
        else -> DEFAULT
      }
    }
  }

  fun accept(suggestion: Suggestion, expected: String): Boolean
}