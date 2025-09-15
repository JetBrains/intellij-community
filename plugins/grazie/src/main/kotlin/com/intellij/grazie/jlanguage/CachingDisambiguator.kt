package com.intellij.grazie.jlanguage

import com.intellij.util.containers.ContainerUtil.createConcurrentSoftKeySoftValueMap
import org.languagetool.AnalyzedSentence
import org.languagetool.AnalyzedTokenReadings
import org.languagetool.tagging.disambiguation.AbstractDisambiguator
import org.languagetool.tagging.disambiguation.Disambiguator

private val cache = createConcurrentSoftKeySoftValueMap<List<AnalyzedTokenReadings>, AnalyzedSentence>()

internal class CachingDisambiguator(private val disambiguator: Disambiguator) : AbstractDisambiguator() {
  override fun disambiguate(input: AnalyzedSentence): AnalyzedSentence =
    cache.computeIfAbsent(copy(input.tokens)) { disambiguator.disambiguate(input) }

  private fun copy(tokens: Array<AnalyzedTokenReadings>): List<AnalyzedTokenReadings> =
    tokens.asSequence()
      .map { token -> AnalyzedTokenReadings(token, token.readings, "") }
      .toList()
}