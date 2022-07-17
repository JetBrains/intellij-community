// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.detection

import ai.grazie.detector.ChainLanguageDetector
import ai.grazie.detector.DefaultLanguageDetectors
import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.tokenizer.word.WhitespaceWordTokenizer.words
import ai.grazie.utils.mpp.FromResourcesDataLoader
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.config.DetectionContext
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.utils.toLinkedSet
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.runBlocking

object LangDetector {
  private val detector by lazy {
    runBlocking {
      DefaultLanguageDetectors.standardForLanguages(
        Language.all.toLinkedSet(),
        FromResourcesDataLoader
      )
    }
  }
  private val cache = ContainerUtil.createConcurrentSoftValueMap<Pair<String, Boolean>, ChainLanguageDetector.ChainDetectionResult>()
  private const val textLimit = 1_000

  private fun detectWithDetails(textToDetect: String, isReliable: Boolean): ChainLanguageDetector.ChainDetectionResult {
    require(textToDetect.length <= textLimit)
    return cache.computeIfAbsent(textToDetect to isReliable) { detector.detectWithDetails(it.first, it.second) }
  }

  /**
   * Get natural language of text.
   *
   * It will perform NGram and Rule-based search for possible languages.
   *
   * @return Language that is detected.
   */
  fun getLanguage(text: String): Language? {
    val detected = detectWithDetails(text.take(textLimit), isReliable = false).result.preferred
    return if (detected == Language.UNKNOWN) null else detected
  }

  /**
   * Get natural language of text if it is enabled in Grazie
   *
   * @return Lang that is detected and enabled in grazie
   */
  fun getLang(text: String): Lang? = getLanguage(text)?.let { language ->
    GrazieConfig.get().availableLanguages.find { lang -> lang.equalsTo(language) }
  }

  /**
   * Update local detection context from text
   */
  fun updateContext(text: CharSequence, context: DetectionContext.Local) {
    val textToDetect = text.take(1_000).toString()
    val details = detectWithDetails(textToDetect, isReliable = true)
    val wordsCount = textToDetect.words().count()
    context.update(text.length, wordsCount, details)
  }
}
