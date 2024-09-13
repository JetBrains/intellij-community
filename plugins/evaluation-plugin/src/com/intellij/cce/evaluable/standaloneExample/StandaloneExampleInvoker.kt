package com.intellij.cce.evaluable.standaloneExample

import com.intellij.cce.core.*
import com.intellij.cce.interpreter.FeatureInvoker

class StandaloneExampleInvoker : FeatureInvoker {
  override fun callFeature(expectedText: String, offset: Int, properties: TokenProperties): Session {
    val session = Session(
      offset = offset,
      expectedText = expectedText,
      completableLength = expectedText.length,
      properties,
    )

    val letter = properties.additionalProperty("Letter")!!.first()
    val text = if (letter.code % 2 == 1) "Vowel" else "Consonant"

    val suggestions = listOf(
      Suggestion(
        text = text,
        presentationText = text,
        source = SuggestionSource.INTELLIJ,
        details = emptyMap(),
        isRelevant = text == expectedText
      )
    )

    val lookup = Lookup(
      "",
      offset,
      suggestions,
      latency = 0,
      features = null,
      selectedPosition = suggestions.indexOfFirst { it.isRelevant },
      isNew = false,
      additionalInfo = emptyMap()
    )

    session.addLookup(lookup)

    return session
  }

  override fun comparator(generated: String, expected: String): Boolean = generated == expected
}