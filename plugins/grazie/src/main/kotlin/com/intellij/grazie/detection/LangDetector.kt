// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.detection

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.config.DetectionContext
import com.intellij.grazie.detector.ChainLanguageDetector
import com.intellij.grazie.detector.DefaultLanguageDetectors
import com.intellij.grazie.detector.model.Language
import com.intellij.grazie.detector.utils.resources.JVMResourceLoader
import com.intellij.grazie.detector.utils.words
import com.intellij.grazie.jlanguage.Lang
import com.intellij.util.containers.ContainerUtil

object LangDetector {
  private val detector by lazy { DefaultLanguageDetectors.standard(JVMResourceLoader) }
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
  @Suppress("MemberVisibilityCanBePrivate")
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
