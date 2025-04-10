// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.i18n

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.text.MessageFormat
import kotlin.test.assertEquals

internal class FormatTest {

  // Basic tests
  @Test
  fun testBasicString() {
    assertFormat("foo")
  }

  @Test
  fun testTwoSingleQuotes() {
    assertFormat("foo''") // Two single quotes produce one single quote
  }

  @Test
  fun testBasicPlaceholder() {
    assertFormat("foo{0}bar", "xxx")
  }

  @Test
  fun testQuotedPlaceholder() {
    assertFormat("foo'{0}'bar") // Quoted placeholder is treated as literal
  }

  @Test
  fun testEmptyPlaceholder() {
    assertFormat("foo{0}bar")
  }

  @Test
  fun testQuotedCurlyBraces() {
    assertFormat("foo'{''}'bar") // Quoted curly braces
  }

  // Escaping quotes
  @Test
  fun testSingleQuote() {
    assertFormat("''") // Just a single quote
  }

  @Test
  fun testTwoSingleQuotesOnly() {
    assertFormat("''''") // Two single quotes
  }

  @Test
  fun testSingleQuoteInMiddle() {
    assertFormat("foo''bar") // Single quote in the middle
  }

  @Test
  fun testSingleQuoteAtBeginning() {
    assertFormat("''foo") // Single quote at the beginning
  }

  @Test
  fun testSingleQuoteAtEnd() {
    assertFormat("foo''") // Single quote at the end
  }

  @Test
  fun testTwoSingleQuotesInMiddle() {
    assertFormat("foo''''bar") // Two single quotes in the middle
  }

  // Escaping curly braces
  @Test
  fun testLeftCurlyBrace() {
    assertFormat("'{'") // Just a left curly brace
  }

  @Test
  fun testRightCurlyBrace() {
    assertFormat("'}'") // Just a right curly brace
  }

  @Test
  fun testBothCurlyBraces() {
    assertFormat("'{}'") // Both curly braces
  }

  @Test
  fun testLeftCurlyBraceInMiddle() {
    assertFormat("foo'{'bar") // Left curly brace in the middle
  }

  @Test
  fun testRightCurlyBraceInMiddle() {
    assertFormat("foo'}'bar") // Right curly brace in the middle
  }

  @Test
  fun testTextInsideQuotedCurlyBraces() {
    assertFormat("'{foo}'") // Text inside quoted curly braces
  }

  // Placeholders
  @Test
  fun testSimplePlaceholder() {
    assertFormat("{0}", "value")
  }

  @Test
  fun testMultiplePlaceholders() {
    assertFormat("{0}{1}", "value1", "value2")
  }

  @Test
  fun testSamePlaceholderTwice() {
    assertFormat("{0}{0}", "value") // Same placeholder used twice
  }

  @Test
  fun testPlaceholdersInDifferentOrder() {
    assertFormat("{1}{0}", "value0", "value1") // Placeholders in different order
  }

  @Test
  fun testPlaceholdersWithText() {
    assertFormat("foo{0}bar{1}baz", "value1", "value2")
  }

  @Test
  fun testSamePlaceholderTwiceWithText() {
    assertFormat("foo{0}bar{0}baz", "value") // Same placeholder used twice
  }

  // Nested quotes and complex escaping
  @Test
  fun testSingleQuotesAroundPlaceholder() {
    assertFormat("foo''{0}''bar", "value") // Single quotes around placeholder
  }

  @Test
  fun testQuoteInsideQuotedPlaceholder() {
    assertFormat("'{0''}'", "value") // Quote inside quoted placeholder
  }

  @Test
  fun testNL() {
    assertFormat("print \\n")
  }

  @Test
  fun testQuotedStringWithPlaceholder() {
    assertFormat("'foo{0}bar'", "value") // Quoted string with placeholder
  }

  @Test
  fun testQuotedPlaceholderWithQuoteInside() {
    assertFormat("foo'{0''}'bar", "value") // Quoted placeholder with quote inside
  }

  @Test
  fun testQuotedCurlyBraceWithQuoteInside() {
    assertFormat("'{'''}'") // Quoted curly brace with quote inside
  }

  // Edge cases
  @Test
  fun testEmptyString() {
    assertFormat("") // Empty string
  }

  @Test
  fun testNullArgument() {
    assertFormat("{0}", "null") // Null argument
  }

  @Test
  fun testEmptyArgument() {
    assertFormat("{0}", "") // Empty argument
  }

  @Test
  fun testEmptyArgumentInMiddle() {
    assertFormat("foo{0}bar", "") // Empty argument in the middle
  }

  // Complex combinations
  @Test
  fun testComplexCombination1() {
    assertFormat("foo''bar{0}baz''qux", "value")
  }

  @Test
  fun testComplexCombination2() {
    assertFormat("foo'{0}'bar{1}baz", "value0", "value1")
  }

  @Test
  fun testComplexCombination3() {
    assertFormat("foo''{0}''bar''{1}''baz", "value0", "value1")
  }

  @Test
  fun testComplexCombination4() {
    assertFormat("'{0}'{1}", "value0", "value1")
  }

  @Test
  fun testComplexCombination5() {
    assertFormat("''{0}''{1}", "value0", "value1")
  }

  private fun assertFormat(raw: String, vararg args: Any) {
    val expected = MessageFormat.format(raw, *args)
    var e: Exception? = null
    val actual = try {
      format(raw, args)
    }
    catch (ex: Exception) {
      e = ex
      ex.message
    }

    assertAll(
      {
        assertEquals(expected = expected,
                     actual = actual,
                     message = "Format '$raw' with args ${args.toList()} should be '$expected', but was '$actual'")
      },
      {
        if (e != null) throw e
      }
    )

  }
}
