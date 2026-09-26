// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.detection

import ai.grazie.detector.ChainLanguageDetector.ChainDetectionResult
import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.tokenizer.word.StandardWordTokenizer.words
import com.intellij.grazie.config.DetectionContext
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.utils.HighlightingUtil
import com.intellij.grazie.utils.LanguageDetectorHolder
import com.intellij.grazie.utils.NaturalTextDetector
import com.intellij.util.containers.ContainerUtil

/**
 * Use [BatchLangDetector] for more accurate results, if possible
 */
object LangDetector {
  private val cache = ContainerUtil.createConcurrentSoftValueMap<String, ChainDetectionResult>()

  internal fun getCleanText(text: TextContent, offset: Int? = null): String? =
    text.substring(offset ?: HighlightingUtil.stripPrefix(text))
      .take(LanguageDetectorHolder.LIMIT)
      .takeIf { NaturalTextDetector.seemsNatural(it) }

  internal fun detectWithDetails(textToDetect: String): ChainDetectionResult {
    require(textToDetect.length <= LanguageDetectorHolder.LIMIT)
    return cache.computeIfAbsent(textToDetect) { LanguageDetectorHolder.get().detectWithDetails(it, isReliable = true) }
  }

  /**
   * Get natural language of text.
   *
   * It will perform NGram and Rule-based search for possible languages.
   *
   * @return Language that is detected.
   */
  fun getLanguage(text: TextContent, offset: Int? = null): Language? {
    val cleanText = getCleanText(text, offset) ?: return null
    return detectWithDetails(cleanText).result.preferred.takeIf { it != Language.UNKNOWN }
  }

  /**
   * Update local detection context from text
   */
  fun updateContext(text: CharSequence, context: DetectionContext.Local) {
    val textToDetect = text.take(LanguageDetectorHolder.LIMIT).toString()
    val details = detectWithDetails(textToDetect)
    val wordsCount = textToDetect.words().count()
    context.update(text.length, wordsCount, details)
  }
}
