// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle

import com.intellij.util.text.matching.KeyboardLayoutUtil
import kotlin.jvm.JvmStatic

/**
 * @author Dmitry Avdeev
 * @see NameUtil.buildMatcher
 */
open class FixingLayoutMatcher(
  pattern: String,
  options: NameUtil.MatchingCaseSensitivity,
  hardSeparators: String,
) : MatcherWithFallback(
  MinusculeMatcherImpl(pattern, options, hardSeparators),
  withFixedLayout(pattern, options, hardSeparators)
) {
  companion object {
    @JvmStatic
    fun fixLayout(pattern: String): String? {
      var hasLetters = false
      var onlyWrongLetters = true
      for (c in pattern) {
        if (Character.isLetter(c)) {
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
          alternatePattern[i] = KeyboardLayoutUtil.getAsciiForChar(c) ?: c
        }
        String(alternatePattern)
      }
      else {
        null
      }
    }

    private fun withFixedLayout(
      pattern: String,
      options: NameUtil.MatchingCaseSensitivity,
      hardSeparators: String,
    ): MinusculeMatcher? {
      val s: String? = fixLayout(pattern)
      return if (s != null && s != pattern) {
        MinusculeMatcherImpl(s, options, hardSeparators)
      }
      else {
        null
      }
    }
  }
}