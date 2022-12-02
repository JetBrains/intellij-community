package com.intellij.cce.core

data class Lookup(
  val prefix: String,
  val suggestions: List<Suggestion>,
  val latency: Long,
  var features: Features? = null,
  val selectedPosition: Int,
  val isNew: Boolean,
  val stubText: String = "",
) {
  fun clearFeatures() {
    features = null
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

      return Lookup(text, suggestions, latency, features, selectedPosition, isNew)
    }
  }
}
