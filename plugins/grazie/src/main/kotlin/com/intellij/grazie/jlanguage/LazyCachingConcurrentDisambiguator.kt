package com.intellij.grazie.jlanguage

import com.intellij.grazie.GraziePlugin
import com.intellij.openapi.util.ClassLoaderUtil.runWithClassLoader
import com.intellij.util.containers.ContainerUtil.createConcurrentSoftKeySoftValueMap
import com.intellij.util.io.computeDetached
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import org.languagetool.AnalyzedSentence
import org.languagetool.AnalyzedTokenReadings
import org.languagetool.Language
import org.languagetool.tagging.disambiguation.AbstractDisambiguator
import org.languagetool.tagging.disambiguation.Disambiguator

private val cache = createConcurrentSoftKeySoftValueMap<List<AnalyzedTokenReadings>, AnalyzedSentence>()

internal class LazyCachingConcurrentDisambiguator(private val jLanguage: Language) : AbstractDisambiguator() {

  @Volatile
  private var disambiguator: Disambiguator? = null
  private val lock = Any()

  override fun disambiguate(input: AnalyzedSentence): AnalyzedSentence {
    ensureInitialized()
    return cache.computeIfAbsent(copy(input.tokens)) { disambiguator!!.disambiguate(input) }
  }

  private fun ensureInitialized() {
    if (disambiguator == null) {
      synchronized(lock) {
        if (disambiguator == null) {
          disambiguator = jLanguage.createDefaultDisambiguator()
        }
      }
    }
  }

  @OptIn(DelicateCoroutinesApi::class)
  suspend fun ensureInitializedAsync() {
    if (disambiguator == null) {
      computeDetached(Dispatchers.Default) {
        runWithClassLoader<Throwable>(GraziePlugin.classLoader) {
          ensureInitialized()
        }
      }
    }
  }

  private fun copy(tokens: Array<AnalyzedTokenReadings>): List<AnalyzedTokenReadings> =
    tokens.asSequence()
      .map { token -> AnalyzedTokenReadings(token, token.readings, "") }
      .toList()
}