package com.intellij.grazie.jlanguage

import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.util.containers.ContainerUtil.createConcurrentSoftKeySoftValueMap
import com.intellij.util.io.computeDetached
import com.intellij.util.progress.withLockCancellable
import kotlinx.coroutines.DelicateCoroutinesApi
import org.languagetool.AnalyzedSentence
import org.languagetool.AnalyzedTokenReadings
import org.languagetool.Language
import org.languagetool.tagging.disambiguation.AbstractDisambiguator
import org.languagetool.tagging.disambiguation.Disambiguator
import java.util.concurrent.locks.ReentrantLock

private val cache = createConcurrentSoftKeySoftValueMap<List<AnalyzedTokenReadings>, AnalyzedSentence>()

internal class LazyCachingDisambiguator(private val jLanguage: Language) : AbstractDisambiguator() {

  @Volatile
  private lateinit var disambiguator: Disambiguator
  override fun disambiguate(input: AnalyzedSentence): AnalyzedSentence {
    ensureInitialized()
    return cache.computeIfAbsent(copy(input.tokens)) { disambiguator.disambiguate(input) }
  }

  @OptIn(DelicateCoroutinesApi::class)
  fun ensureInitialized() {
    if (!this::disambiguator.isInitialized) {
      disambiguator = runBlockingCancellable {
        computeDetached {
          jLanguage.createDefaultDisambiguator()
        }
      }
    }
  }

  private fun copy(tokens: Array<AnalyzedTokenReadings>): List<AnalyzedTokenReadings> =
    tokens.asSequence()
      .map { token -> AnalyzedTokenReadings(token, token.readings, "") }
      .toList()
}