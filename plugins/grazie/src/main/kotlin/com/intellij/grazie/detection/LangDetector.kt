// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.detection

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.config.DetectionContext
import com.intellij.grazie.detector.chain.ChainDetectorBuilder
import com.intellij.grazie.detector.model.Language
import com.intellij.grazie.ide.fus.GrazieFUSCounter
import com.intellij.grazie.ide.msg.GrazieStateLifecycle
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.utils.lazyConfig

object LangDetector : GrazieStateLifecycle {
  private var available: Set<Lang> by lazyConfig(this::init)

  private val detector by lazy { ChainDetectorBuilder.standard() }

  /**
   * Get natural language of text.
   *
   * It will perform NGram and Rule-based search for possible languages.
   *
   * @return Language that is detected.
   */
  @Suppress("MemberVisibilityCanBePrivate")
  fun getLanguage(text: String): Language? {
    val detected = detector.detect(text.take(1_000))

    if (detected.preferred == Language.UNKNOWN) return null

    return detected.preferred
  }

  /**
   * Get natural language of text, if it is enabled in Grazie
   *
   * @return Lang that is detected and enabled in grazie
   */
  fun getLang(text: String) = getLanguage(text)?.let {
    available.find { lang -> lang.equalsTo(it) }
  }

  /**
   * Update local detection context from text
   */
  fun updateContext(text: CharSequence, context: DetectionContext.Local) {
    val details = detector.detectWithDetails(text.take(1_000))
    context.update(text.length, details)
  }

  override fun init(state: GrazieConfig.State) {
    available = state.availableLanguages
  }

  override fun update(prevState: GrazieConfig.State, newState: GrazieConfig.State) {
    if (prevState.availableLanguages != newState.availableLanguages) {
      init(newState)
    }
  }
}
