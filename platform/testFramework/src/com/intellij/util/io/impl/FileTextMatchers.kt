// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.impl

import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.io.FileTextMatcher

object FileTextMatchers {
  val exact = object : FileTextMatcher {
    override fun matches(actualText: String, expectedText: String): Boolean {
      return actualText == expectedText
    }
  }
  val ignoreBlankLines = object : FileTextMatcher {
    override fun matches(actualText: String, expectedText: String): Boolean {
      return actualText.lines().filter { it.isNotBlank() } == expectedText.lines().filter { it.isNotBlank() }
    }
  }
  val ignoreXmlFormatting = object : FileTextMatcher {
    override fun matches(actualText: String, expectedText: String): Boolean {
      val trimmed = expectedText.trim()
      if (trimmed.startsWith('<') && trimmed.endsWith('>')) {
        return JDOMUtil.areElementsEqual(JDOMUtil.load(actualText), JDOMUtil.load(expectedText))
      }
      return actualText == expectedText
    }
  }
}