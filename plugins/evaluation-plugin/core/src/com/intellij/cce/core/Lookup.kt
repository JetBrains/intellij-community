// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.core

import com.intellij.openapi.diagnostic.thisLogger
import kotlin.math.max

const val DEFAULT_LOOKUP_ELEMENT_TYPE: String = "No element type associated to textRange"

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

  /**
   * if compatibleElementTypes is empty, assume element_type matching is not enabled, mark lookup as compatible
   * if compatibleElementTypes is not empty, assume element_type matching is enabled and check if element_type exists within lookup and is within compatibleElementTypes
   */
  fun checkElementTypeCompatibility(compatibleElementTypes: List<String>): Boolean {
    if (compatibleElementTypes.isEmpty()) return true
    val lookupElementType = additionalInfo["element_type"] as? String ?: return true
    if (compatibleElementTypes.contains(lookupElementType)) return true
    if(lookupElementType.equals(DEFAULT_LOOKUP_ELEMENT_TYPE)) thisLogger().warn("Element Type matching enabled for metrics calculation, yet No element type associated to lookup")
    return false
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
      suggestions.forEach { it.isRelevant = comparator(it.text, expectedText.substring(max(0, startOffset - prefix.length))) }

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
