// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle

import com.intellij.util.text.matching.KeyboardLayoutConverter
import com.intellij.util.text.matching.MatchingMode
import kotlin.jvm.JvmStatic

/**
 * @author Dmitry Avdeev
 * @see NameUtil.buildMatcher
 */
open class FixingLayoutMatcher(
  pattern: String,
  matchingMode: MatchingMode,
  hardSeparators: String,
  keyboardLayoutConverter: KeyboardLayoutConverter,
) : MatcherWithFallback(
  MinusculeMatcherImpl(pattern, matchingMode, hardSeparators),
  withFixedLayout(pattern, matchingMode, hardSeparators, keyboardLayoutConverter)
) {

  @Deprecated("Use {@link #FixingLayoutMatcher(String, MatchingCaseSensitivity, String, KeyboardLayoutConverter)} instead")
  constructor(pattern: String,
              options: NameUtil.MatchingCaseSensitivity,
              hardSeparators: String) : this(pattern, options.matchingMode(), hardSeparators, PlatformKeyboardLayoutConverter)

  companion object {
    @JvmStatic
    fun fixLayout(pattern: String, keyboardLayoutConverter: KeyboardLayoutConverter): String? {
      var hasLetters = false
      var onlyWrongLetters = true
      for (c in pattern) {
        if (c.isLetter()) {
          hasLetters = true
          if (c <= '\u007f') {
            onlyWrongLetters = false
            break
          }
        }
      }

      return if (hasLetters && onlyWrongLetters) {
        val alternatePattern = CharArray(pattern.length)
        pattern.forEachIndexed { i, c ->
          alternatePattern[i] = keyboardLayoutConverter.convert(c) ?: c
        }
        alternatePattern.concatToString()
      }
      else {
        null
      }
    }

    private fun withFixedLayout(
      pattern: String,
      matchingMode: MatchingMode,
      hardSeparators: String,
      keyboardLayoutConverter: KeyboardLayoutConverter
    ): MinusculeMatcher? {
      val s: String? = fixLayout(pattern, keyboardLayoutConverter)
      return if (s != null && s != pattern) {
        MinusculeMatcherImpl(s, matchingMode, hardSeparators)
      }
      else {
        null
      }
    }
  }
}