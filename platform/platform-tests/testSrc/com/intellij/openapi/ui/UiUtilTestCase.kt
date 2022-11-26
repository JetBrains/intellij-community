// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import org.junit.jupiter.api.Assertions.assertEquals

abstract class UiUtilTestCase {

  fun assertShortenText(
    expectedShortText: String,
    text: String,
    maxTextWidth: Int,
    minTextPrefixLength: Int = 1,
    minTextSuffixLength: Int = 1,
    maxTextPrefixRatio: Float = 0.5f,
  ) {
    val actualShortText = shortenTextWithEllipsis(
      text,
      maxTextWidth = maxTextWidth,
      minTextPrefixLength = minTextPrefixLength,
      minTextSuffixLength = minTextSuffixLength,
      maxTextPrefixRatio = maxTextPrefixRatio,
      getTextWidth = {
        it.sumOf { c ->
          when (c) {
            in '0'..'9' -> c.code - '0'.code
            else -> 1
          }
        }
      },
      useEllipsisSymbol = false
    )
    assertEquals(expectedShortText, actualShortText)
  }
}