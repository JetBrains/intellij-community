// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.core

data class Lookup(
  val prefix: String,
  val offset: Int,
  val suggestions: List<Suggestion>,
  val latency: Long,
  var features: Features? = null,
  val selectedPosition: Int,
  val isNew: Boolean,
  val additionalInfo: Map<String, Any> = emptyMap()
) {

  constructor(
    prefix: String,
    offset: Int,
    suggestions: List<Suggestion>,
    latency: Long,
    features: Features? = null,
    isNew: Boolean,
    additionalInfo: Map<String, Any> = emptyMap()
  ) : this(
      prefix = prefix,
      offset = offset,
      suggestions = suggestions,
      latency = latency,
      features = features,
      selectedPosition = calculateSelectedPosition(suggestions),
      isNew = isNew,
      additionalInfo = additionalInfo
      )

  fun clearFeatures() {
    features = null
  }

  fun selectedWithoutPrefix(): String? {
    if (selectedPosition == -1) return null

    return suggestions.getOrNull(selectedPosition)?.text?.drop(prefix.length)?.takeIf { it.isNotEmpty() }
  }

  fun withSuggestions(suggestions_: List<Suggestion>): Lookup {
    return Lookup(
      prefix = this.prefix,
      offset = this.offset,
      suggestions = suggestions_,
      latency = this.latency,
      features = this.features,
      isNew = this.isNew,
      additionalInfo = additionalInfo
    )
  }
  companion object {
    fun fromExpectedText(
      expectedText: String,
      prefix: String,
      suggestions: List<Suggestion>,
      latency: Long,
      features: Features? = null,
      isNew: Boolean = false,
      startOffset: Int = 0,
      comparator: (String, String) -> Boolean,
      additionalInfo: Map<String, Any> = emptyMap()
    ): Lookup {
      suggestions.forEach { it.isRelevant = comparator(it.text, expectedText.substring(startOffset - prefix.length)) }

      return Lookup(
        prefix = prefix,
        offset = startOffset,
        suggestions = suggestions,
        latency = latency,
        features = features,
        isNew = isNew,
        additionalInfo = additionalInfo
      )
    }

    private fun calculateSelectedPosition(suggestions: List<Suggestion>): Int {
      val maxLength = suggestions.filter { it.isRelevant }.maxOfOrNull { it.text.length }
      return suggestions.indexOfFirst { it.isRelevant && it.text.length == maxLength }
        .let { if (it < 0) -1 else it }
    }
  }
}
