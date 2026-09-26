// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * @see StringUtils
 * @author Denes Daniel
 */
class StringUtilsTest {

  private fun <T> assertCommutative(function: (T, T) -> Int, in1: T, in2: T, expected: Int) {
    assertThat(function(in1, in2)).isEqualTo(expected)
    assertThat(function(in2, in1)).isEqualTo(expected)
  }

  @Test
  fun `common prefix length test`() {
    assertCommutative(StringUtils::commonPrefixLength, "", "", 0)
    assertCommutative(StringUtils::commonPrefixLength, "", "a", 0)
    assertCommutative(StringUtils::commonPrefixLength, "", "X", 0)

    assertCommutative(StringUtils::commonPrefixLength, "a", "a", 1)
    assertCommutative(StringUtils::commonPrefixLength, "a", "A", 0)
    assertCommutative(StringUtils::commonPrefixLength, "a", "x", 0)
    assertCommutative(StringUtils::commonPrefixLength, "x", "X", 0)
    assertCommutative(StringUtils::commonPrefixLength, "X", "X", 1)

    assertCommutative(StringUtils::commonPrefixLength, "abc", "", 0)
    assertCommutative(StringUtils::commonPrefixLength, "abc", "a", 1)
    assertCommutative(StringUtils::commonPrefixLength, "abc", "A", 0)
    assertCommutative(StringUtils::commonPrefixLength, "abc", "ab", 2)
    assertCommutative(StringUtils::commonPrefixLength, "abc", "aB", 1)
    assertCommutative(StringUtils::commonPrefixLength, "abc", "AB", 0)
    assertCommutative(StringUtils::commonPrefixLength, "abc", "Ab", 0)
    assertCommutative(StringUtils::commonPrefixLength, "abc", "abc", 3)
    assertCommutative(StringUtils::commonPrefixLength, "abc", "abC", 2)
    assertCommutative(StringUtils::commonPrefixLength, "abc", "aBC", 1)
    assertCommutative(StringUtils::commonPrefixLength, "abc", "ABC", 0)
    assertCommutative(StringUtils::commonPrefixLength, "abc", "ABc", 0)
    assertCommutative(StringUtils::commonPrefixLength, "abc", "Abc", 0)

    assertCommutative(StringUtils::commonPrefixLength, "11111", "11111", 5)
    assertCommutative(StringUtils::commonPrefixLength, "11111", "11110", 4)
    assertCommutative(StringUtils::commonPrefixLength, "11111", "11101", 3)
    assertCommutative(StringUtils::commonPrefixLength, "11111", "11011", 2)
    assertCommutative(StringUtils::commonPrefixLength, "11111", "10111", 1)
    assertCommutative(StringUtils::commonPrefixLength, "11111", "01111", 0)

    assertCommutative(StringUtils::commonPrefixLength, "foo-bar-1.0.jar", "foo", 3)
    assertCommutative(StringUtils::commonPrefixLength, "foo-bar-1.0.jar", "foo.bar", 3)
    assertCommutative(StringUtils::commonPrefixLength, "foo-bar-1.0.jar", "foo-ton", 4)
    assertCommutative(StringUtils::commonPrefixLength, "foo-bar-1.0.jar", "foo-baz", 6)
    assertCommutative(StringUtils::commonPrefixLength, "foo-bar-1.0.jar", "foo-bar", 7)
    assertCommutative(StringUtils::commonPrefixLength, "foo-bar-1.0.jar", "foo-bar.testing", 7)
    assertCommutative(StringUtils::commonPrefixLength, "foo-bar-1.0.jar", "foo-bar-testing", 8)
  }

  @Test
  fun `common suffix length test`() {
    assertCommutative(StringUtils::commonSuffixLength, "", "", 0)
    assertCommutative(StringUtils::commonSuffixLength, "", "a", 0)
    assertCommutative(StringUtils::commonSuffixLength, "", "X", 0)

    assertCommutative(StringUtils::commonSuffixLength, "a", "a", 1)
    assertCommutative(StringUtils::commonSuffixLength, "a", "A", 0)
    assertCommutative(StringUtils::commonSuffixLength, "a", "x", 0)
    assertCommutative(StringUtils::commonSuffixLength, "x", "X", 0)
    assertCommutative(StringUtils::commonSuffixLength, "X", "X", 1)

    assertCommutative(StringUtils::commonSuffixLength, "abc", "", 0)
    assertCommutative(StringUtils::commonSuffixLength, "abc", "c", 1)
    assertCommutative(StringUtils::commonSuffixLength, "abc", "C", 0)
    assertCommutative(StringUtils::commonSuffixLength, "abc", "bc", 2)
    assertCommutative(StringUtils::commonSuffixLength, "abc", "Bc", 1)
    assertCommutative(StringUtils::commonSuffixLength, "abc", "BC", 0)
    assertCommutative(StringUtils::commonSuffixLength, "abc", "bC", 0)
    assertCommutative(StringUtils::commonSuffixLength, "abc", "abc", 3)
    assertCommutative(StringUtils::commonSuffixLength, "abc", "Abc", 2)
    assertCommutative(StringUtils::commonSuffixLength, "abc", "ABc", 1)
    assertCommutative(StringUtils::commonSuffixLength, "abc", "ABC", 0)
    assertCommutative(StringUtils::commonSuffixLength, "abc", "aBC", 0)
    assertCommutative(StringUtils::commonSuffixLength, "abc", "abC", 0)

    assertCommutative(StringUtils::commonSuffixLength, "11111", "11111", 5)
    assertCommutative(StringUtils::commonSuffixLength, "11111", "01111", 4)
    assertCommutative(StringUtils::commonSuffixLength, "11111", "10111", 3)
    assertCommutative(StringUtils::commonSuffixLength, "11111", "11011", 2)
    assertCommutative(StringUtils::commonSuffixLength, "11111", "11101", 1)
    assertCommutative(StringUtils::commonSuffixLength, "11111", "11110", 0)

    assertCommutative(StringUtils::commonSuffixLength, "foo-bar.jar", "foo-bar-testing.jar", 4)
    assertCommutative(StringUtils::commonSuffixLength, "foo-bar-1.0.jar", "foo-bar-testing-1.0.jar", 8)
    assertCommutative(StringUtils::commonSuffixLength, "foo-bar-sources.jar", "foo-bar-testing-sources.jar", 12)
    assertCommutative(StringUtils::commonSuffixLength, "foo-bar-1.0-sources.jar", "foo-bar-testing-1.0-sources.jar", 16)
    assertCommutative(StringUtils::commonSuffixLength, "foo-bar.src.jar", "foo-bar-testing.src.jar", 8)
    assertCommutative(StringUtils::commonSuffixLength, "foo-bar-1.0.src.jar", "foo-bar-testing-1.0.src.jar", 12)
    assertCommutative(StringUtils::commonSuffixLength, "foo-bar-sources.src.jar", "foo-bar-testing-sources.src.jar", 16)
    assertCommutative(StringUtils::commonSuffixLength, "foo-bar-1.0-sources.src.jar", "foo-bar-testing-1.0-sources.src.jar", 20)
  }
}