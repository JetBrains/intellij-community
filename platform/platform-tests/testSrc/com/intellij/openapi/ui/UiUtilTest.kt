// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import org.junit.jupiter.api.Test

class UiUtilTest : UiUtilTestCase() {

  @Test
  fun `test shorten text with ellipsis`() {
    assertShortenText("111111111111111", "111111111111111", 20)
    assertShortenText("111111111111111", "111111111111111", 15)
    assertShortenText("1111...1111", "111111111111111", 11)
    assertShortenText("1...1", "111111111111111", 5)

    assertShortenText("1...1", "111111111111111", 5, minTextPrefixLength = 1, minTextSuffixLength = 0)
    assertShortenText("1...", "111111111111111", 4, minTextPrefixLength = 1, minTextSuffixLength = 0)
    assertShortenText("1...", "111111111111111", 0, minTextPrefixLength = 1, minTextSuffixLength = 0)
    assertShortenText("11...", "111111111111111", 0, minTextPrefixLength = 2, minTextSuffixLength = 0)
    assertShortenText("111...", "111111111111111", 0, minTextPrefixLength = 3, minTextSuffixLength = 0)

    assertShortenText("1...1", "111111111111111", 5, minTextPrefixLength = 0, minTextSuffixLength = 1)
    assertShortenText("...1", "111111111111111", 4, minTextPrefixLength = 0, minTextSuffixLength = 1)
    assertShortenText("...1", "111111111111111", 0, minTextPrefixLength = 0, minTextSuffixLength = 1)
    assertShortenText("...11", "111111111111111", 0, minTextPrefixLength = 0, minTextSuffixLength = 2)
    assertShortenText("...111", "111111111111111", 0, minTextPrefixLength = 0, minTextSuffixLength = 3)

    assertShortenText("1...1", "111111111111111", 5, minTextPrefixLength = 1, minTextSuffixLength = 1)
    assertShortenText("1...1", "111111111111111", 4, minTextPrefixLength = 1, minTextSuffixLength = 1)
    assertShortenText("1...1", "111111111111111", 0, minTextPrefixLength = 1, minTextSuffixLength = 1)
    assertShortenText("11...11", "111111111111111", 0, minTextPrefixLength = 2, minTextSuffixLength = 2)
    assertShortenText("111...111", "111111111111111", 0, minTextPrefixLength = 3, minTextSuffixLength = 3)

    assertShortenText("1...111111111", "111111111111111", 13, maxTextPrefixRatio = 0.0f)
    assertShortenText("111...1111111", "111111111111111", 13, maxTextPrefixRatio = 0.3f)
    assertShortenText("11111...11111", "111111111111111", 13, maxTextPrefixRatio = 0.5f)
    assertShortenText("1111111...111", "111111111111111", 13, maxTextPrefixRatio = 0.7f)
    assertShortenText("111111111...1", "111111111111111", 13, maxTextPrefixRatio = 1.0f)

    // number in symbol means width of symbol for tests
    assertShortenText("111112222211111", "111112222211111", 20)
    assertShortenText("111112...2211111", "111112222211111", 19)
    assertShortenText("111112...211111", "111112222211111", 17)
    assertShortenText("11111...211111", "111112222211111", 15)
    assertShortenText("111...1111", "111112222211111", 10)
    assertShortenText("11111...22222", "111111111122222", 18)
    assertShortenText("1111...2222", "111111111122222", 15)
    assertShortenText("11...22", "111111111122222", 10)

    assertShortenText("", "", 0, minTextPrefixLength = 2, minTextSuffixLength = 2)
    assertShortenText("", "", 1, minTextPrefixLength = 2, minTextSuffixLength = 2)
    assertShortenText("1", "1", 0, minTextPrefixLength = 2, minTextSuffixLength = 2)
    assertShortenText("1", "1", 1, minTextPrefixLength = 2, minTextSuffixLength = 2)
    assertShortenText("11", "11", 0, minTextPrefixLength = 2, minTextSuffixLength = 2)
    assertShortenText("1111", "1111", 0, minTextPrefixLength = 2, minTextSuffixLength = 2)
    assertShortenText("11111", "11111", 0, minTextPrefixLength = 2, minTextSuffixLength = 2)
    assertShortenText("111111", "111111", 0, minTextPrefixLength = 2, minTextSuffixLength = 2)
    assertShortenText("11...11", "112211", 0, minTextPrefixLength = 2, minTextSuffixLength = 2)
    assertShortenText("1111111", "1111111", 0, minTextPrefixLength = 2, minTextSuffixLength = 2)
    assertShortenText("11...11", "1112111", 0, minTextPrefixLength = 2, minTextSuffixLength = 2)
    assertShortenText("11...11", "11111111", 0, minTextPrefixLength = 2, minTextSuffixLength = 2)
  }
}