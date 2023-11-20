// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.text.nullize

object AssertionMessageParser {

  // JUnit 5: assertEquals | assertSame: org.junit.jupiter.api.Assertions
  private val JUNIT5_ASSERT_EQUALS_EXTRACTOR =
    "((?<message>.+) ==> )?expected: <(?<expected>.*)> but was: <(?<actual>.*)>"
      .toRegex(RegexOption.DOT_MATCHES_ALL)
  private val JUNIT5_ASSERT_EQUALS_WITH_CLASS_EXTRACTOR =
    "((?<message>.+) ==> )?expected: (?<expected>[^<]+<(.*)>) but was: (?<actual>[^<]+<(.*)>)"
      .toRegex(RegexOption.DOT_MATCHES_ALL)

  // JUnit 4: assertEquals: org.junit.Assert | junit.framework.Assert | junit.framework.TestCase | org.testng.AssertJUnit
  private val JUNIT4_ASSERT_EQUALS_EXTRACTOR =
    "((?<message>.+) )?expected:<(?<expected>.*)> but was:<(?<actual>.*)>"
      .toRegex(RegexOption.DOT_MATCHES_ALL)
  private val JUNIT4_ASSERT_EQUALS_WITH_CLASS_EXTRACTOR =
    "((?<message>.+) )?expected: (?<expected>[^<]+<.*>) but was: (?<actual>[^<]+<.*>)"
      .toRegex(RegexOption.DOT_MATCHES_ALL)

  // JUnit 4: assertSame: org.junit.Assert | junit.framework.Assert | junit.framework.TestCase | org.testng.AssertJUnit
  private val JUNIT4_ASSERT_SAME_EXTRACTOR =
    "((?<message>.+) )?expected same:<(?<expected>.*)> was not:<(?<actual>.*)>"
      .toRegex(RegexOption.DOT_MATCHES_ALL)

  // AssertJ: assertEquals: org.assertj.core.api.Assertions
  private val ASSERTJ_ASSERT_EQUALS_EXTRACTOR =
    "(?<message>)\nexpected: (?<expected>.*)\n but was: (?<actual>.*)"
      .toRegex(RegexOption.DOT_MATCHES_ALL)

  // AssertJ: assertSame: org.assertj.core.api.Assertions
  private val ASSERTJ_ASSERT_SAME_EXTRACTOR =
    "(?<message>)\nExpecting actual:\n {2}(?<actual>.*)\nand:\n {2}(?<expected>.*)\nto refer to the same object"
      .toRegex(RegexOption.DOT_MATCHES_ALL)

  // Test NG: assertEquals | assertSame: org.testng.Assert
  private val TESTNG_ASSERT_EQUALS_EXTRACTOR =
    "((?<message>.+) )?expected \\[(?<expected>.*)] but found \\[(?<actual>.*)]"
      .toRegex(RegexOption.DOT_MATCHES_ALL)

  @JvmStatic
  fun parse(assertionMessage: String): Result? {
    return parse(assertionMessage, JUNIT5_ASSERT_EQUALS_EXTRACTOR)
           ?: parse(assertionMessage, JUNIT5_ASSERT_EQUALS_WITH_CLASS_EXTRACTOR)
           ?: parse(assertionMessage, JUNIT4_ASSERT_EQUALS_EXTRACTOR)
           ?: parse(assertionMessage, JUNIT4_ASSERT_EQUALS_WITH_CLASS_EXTRACTOR)
           ?: parse(assertionMessage, JUNIT4_ASSERT_SAME_EXTRACTOR)
           ?: parse(assertionMessage, ASSERTJ_ASSERT_EQUALS_EXTRACTOR)
           ?: parse(assertionMessage, ASSERTJ_ASSERT_SAME_EXTRACTOR)
           ?: parse(assertionMessage, TESTNG_ASSERT_EQUALS_EXTRACTOR)
  }

  private fun parse(assertionMessage: String, regex: Regex): Result? {
    val canonicalAssertionMessage = StringUtil.convertLineSeparators(assertionMessage)
    val matchesResult = regex.matchEntire(canonicalAssertionMessage) ?: return null
    val expected = matchesResult.groups["expected"] ?: return null
    val actual = matchesResult.groups["actual"] ?: return null
    val message = matchesResult.groups["message"]
    return Result(expected.value, actual.value, message?.value.nullize())
  }

  class Result(
    val expected: String,
    val actual: String,
    val message: String?
  )
}