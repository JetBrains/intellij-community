// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle

import com.intellij.psi.codeStyle.FixingLayoutMatcher.Companion.fixLayout
import com.intellij.util.text.Matcher
import com.intellij.util.text.NameUtilCore

class WordPrefixMatcher(pattern: String) : Matcher {
  private val myPatternWords = splitToWords(pattern)
  private val myFallbackPatternWords = run {
    val fixedLayout = fixLayout(pattern)
    if (fixedLayout != null && fixedLayout != pattern) NameUtilCore.nameToWordList(fixedLayout) else null
  }

  override fun matches(name: String): Boolean {
    val nameWords = splitToWords(name)
    return matches(myPatternWords, nameWords) || myFallbackPatternWords != null && matches(myFallbackPatternWords, nameWords)
  }

  private companion object {
    private val wordRegex = "[\\s-/]".toRegex()

    private fun splitToWords(string: String): List<String> {
      return string.split(wordRegex)
    }

    private fun matches(patternWords: List<String>, nameWords: List<String>): Boolean {
      return patternWords.all { pw ->
        nameWords.any { nw ->
          nw.startsWith(pw, ignoreCase = true)
        }
      }
    }
  }
}