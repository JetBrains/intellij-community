// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.impl

import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.io.FileTextMatcher

object FileTextMatchers {
  val exact: FileTextMatcher = object : FileTextMatcher {
    override fun matches(actualText: String, expectedText: String): Boolean =
      actualText == expectedText
  }

  val lines: FileTextMatcher = object : FileTextMatcher {
    override fun matches(actualText: String, expectedText: String): Boolean =
      actualText.lines() == expectedText.lines()
  }

  val ignoreBlankLines: FileTextMatcher = object : FileTextMatcher {
    override fun matches(actualText: String, expectedText: String): Boolean =
      actualText.lines().filter { it.isNotBlank() } == expectedText.lines().filter { it.isNotBlank() }
  }

  val ignoreXmlFormatting: FileTextMatcher = object : FileTextMatcher {
    override fun matches(actualText: String, expectedText: String): Boolean {
      val trimmed = expectedText.trim()
      if (trimmed.startsWith('<') && trimmed.endsWith('>')) {
        return JDOMUtil.areElementsEqual(JDOMUtil.load(actualText), JDOMUtil.load(expectedText))
      }
      return actualText == expectedText
    }
  }
}
