// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.detection

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.config.DetectionContext
import com.intellij.grazie.detector.DefaultLanguageDetectors
import com.intellij.grazie.detector.model.Language
import com.intellij.grazie.detector.utils.resources.JVMResourceLoader
import com.intellij.grazie.detector.utils.words

object LangDetector {
  private val detector by lazy { DefaultLanguageDetectors.standard(JVMResourceLoader) }

  /**
   * Get natural language of text.
   *
   * It will perform NGram and Rule-based search for possible languages.
   *
   * @return Language that is detected.
   */
  @Suppress("MemberVisibilityCanBePrivate")
  fun getLanguage(text: String): Language? {
    val detected = detector.detect(text.take(1_000), isReliable = false)

    if (detected.preferred == Language.UNKNOWN) return null

    return detected.preferred
  }

  /**
   * Get natural language of text, if it is enabled in Grazie
   *
   * @return Lang that is detected and enabled in grazie
   */
  fun getLang(text: String) = getLanguage(text)?.let {
    GrazieConfig.get().availableLanguages.find { lang -> lang.equalsTo(it) }
  }

  /**
   * Update local detection context from text
   */
  fun updateContext(text: CharSequence, context: DetectionContext.Local) {
    val textToDetect = text.take(1_000)
    val details = detector.detectWithDetails(textToDetect, isReliable = true)
    val wordsCount = textToDetect.words().count()
    context.update(text.length, wordsCount, details)
  }
}
