// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.detection

import ai.grazie.detector.ChainLanguageDetector
import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.tokenizer.word.StandardWordTokenizer.words
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.config.DetectionContext
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.utils.LanguageDetectorHolder
import com.intellij.util.containers.ContainerUtil

/**
 * Use [BatchLangDetector] for more accurate results, if possible
 */
object LangDetector {
  private val cache = ContainerUtil.createConcurrentSoftValueMap<Pair<String, Boolean>, ChainLanguageDetector.ChainDetectionResult>()

  private fun detectWithDetails(textToDetect: String, isReliable: Boolean): ChainLanguageDetector.ChainDetectionResult {
    require(textToDetect.length <= LanguageDetectorHolder.LIMIT)
    return cache.computeIfAbsent(textToDetect to isReliable) { LanguageDetectorHolder.get().detectWithDetails(it.first, it.second) }
  }

  /**
   * Get natural language of text.
   *
   * It will perform NGram and Rule-based search for possible languages.
   *
   * @return Language that is detected.
   */
  fun getLanguage(text: String): Language? {
    val detected = detectWithDetails(text.take(LanguageDetectorHolder.LIMIT), isReliable = false).result.preferred
    return if (detected == Language.UNKNOWN) null else detected
  }

  /**
   * Get natural language of text if it is enabled in Grazie
   *
   * @return Lang that is detected and enabled in grazie
   */
  @Suppress("unused") // preserved to not break binary compatibility
  fun getLang(text: String): Lang? = getLanguage(text)?.let { language ->
    GrazieConfig.get().availableLanguages.find { lang -> lang.equalsTo(language) }
  }

  /**
   * Update local detection context from text
   */
  fun updateContext(text: CharSequence, context: DetectionContext.Local) {
    val textToDetect = text.take(LanguageDetectorHolder.LIMIT).toString()
    val details = detectWithDetails(textToDetect, isReliable = true)
    val wordsCount = textToDetect.words().count()
    context.update(text.length, wordsCount, details)
  }
}
