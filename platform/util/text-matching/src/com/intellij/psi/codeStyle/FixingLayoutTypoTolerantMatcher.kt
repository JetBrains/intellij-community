// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle

import com.intellij.psi.codeStyle.FixingLayoutMatcher.Companion.fixLayout
import com.intellij.util.text.matching.MatchingMode
import kotlin.jvm.JvmStatic

internal object FixingLayoutTypoTolerantMatcher {
  @JvmStatic
  fun create(pattern: String, matchingMode: MatchingMode, hardSeparators: String): MinusculeMatcher {
    val mainMatcher = TypoTolerantMatcher(pattern, matchingMode, hardSeparators)
    val s = fixLayout(pattern)
    return if (s != null && s != pattern) {
      val fallbackMatcher = TypoTolerantMatcher(s, matchingMode, hardSeparators)
      MatcherWithFallback(mainMatcher, fallbackMatcher)
    }
    else {
      mainMatcher
    }
  }
}