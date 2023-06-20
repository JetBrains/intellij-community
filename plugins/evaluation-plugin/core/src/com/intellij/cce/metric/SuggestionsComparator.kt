// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.Language
import com.intellij.cce.core.Suggestion

interface SuggestionsComparator {
  companion object {
    val DEFAULT = object : SuggestionsComparator {
      override fun accept(suggestion: Suggestion, expected: String): Boolean = suggestion.text == expected
    }

    fun create(language: Language): SuggestionsComparator {
      return when {
        language == Language.KOTLIN -> KotlinSuggestionsComparator()
        language == Language.CPP -> FirstTokenComparator()
        else -> DEFAULT
      }
    }
  }

  fun accept(suggestion: Suggestion, expected: String): Boolean
}