package com.intellij.grazie.jlanguage

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.grazie.GraziePlugin
import com.intellij.grazie.utils.FirstInvocationCancellationGuard
import com.intellij.openapi.util.ClassLoaderUtil.runWithClassLoader
import com.intellij.util.io.computeDetached
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import org.languagetool.AnalyzedSentence
import org.languagetool.AnalyzedTokenReadings
import org.languagetool.JLanguageTool
import org.languagetool.Language
import org.languagetool.tagging.disambiguation.AbstractDisambiguator
import org.languagetool.tagging.disambiguation.Disambiguator

private val cache = Caffeine.newBuilder()
  .softValues()
  .maximumSize(CACHE_SIZE)
  .build<List<AnalyzedTokenReadings>, AnalyzedSentence>()

internal class LazyCachingConcurrentDisambiguator(private val jLanguage: Language) : AbstractDisambiguator() {

  @Volatile
  private var disambiguator: Disambiguator? = null
  private val lock = Any()
  private val cancellationGuard = FirstInvocationCancellationGuard()

  override fun disambiguate(input: AnalyzedSentence): AnalyzedSentence = disambiguate(input, null)

  override fun disambiguate(input: AnalyzedSentence, checkCanceled: JLanguageTool.CheckCancelledCallback?): AnalyzedSentence {
    ensureInitialized()
    return cancellationGuard.withCheckCancelled {
      cache.get(copy(input.tokens)) {
        disambiguator!!.disambiguate(input, checkCanceled)
      }
    }
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
    tokens.map { token -> AnalyzedTokenReadings(token, token.readings, "") }
}