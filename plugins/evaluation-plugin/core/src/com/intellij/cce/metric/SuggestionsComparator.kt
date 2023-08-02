// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Suggestion

interface SuggestionsComparator {
  companion object {
    val DEFAULT = object : SuggestionsComparator {
      override fun accept(suggestion: Suggestion, expected: String, lookup: Lookup): Boolean = suggestion.text == expected
    }
  }

  fun accept(suggestion: Suggestion, expected: String, lookup: Lookup): Boolean
}