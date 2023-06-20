// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.core

import com.intellij.cce.evaluable.golf.firstToken

data class Lookup(
  val prefix: String,
  val offset: Int,
  val suggestions: List<Suggestion>,
  val latency: Long,
  var features: Features? = null,
  val selectedPosition: Int,
  val isNew: Boolean
) {
  fun clearFeatures() {
    features = null
  }

  fun selectedWithoutPrefix(): String? {
    if (selectedPosition == -1) return null

    return suggestions.getOrNull(selectedPosition)?.let {
      if (it.kind == SuggestionKind.TOKEN) firstToken(it.text) else it.text
    }?.drop(prefix.length)?.takeIf { it.isNotEmpty() }
  }

  companion object {
    fun fromExpectedText(
      expectedText: String,
      text: String,
      suggestions: List<Suggestion>,
      latency: Long,
      features: Features? = null,
      isNew: Boolean = false
    ): Lookup {
      val selectedPosition = suggestions.indexOfFirst { it.text == expectedText }
        .let { if (it < 0) -1 else it }

      return Lookup(text, text.length, suggestions, latency, features, selectedPosition, isNew)
    }
  }
}
