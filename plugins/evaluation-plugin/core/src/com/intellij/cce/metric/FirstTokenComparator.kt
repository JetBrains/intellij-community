// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Suggestion

class FirstTokenComparator : SuggestionsComparator {
  override fun accept(suggestion: Suggestion, expected: String, lookup: Lookup): Boolean {
    var firstToken = ""
    for (ch in suggestion.text.trim()) {
      if (ch.isLetter() || ch == '_' || ch.isDigit() && firstToken.isNotBlank()) firstToken += ch
      else break
    }
    return firstToken == expected
  }
}
