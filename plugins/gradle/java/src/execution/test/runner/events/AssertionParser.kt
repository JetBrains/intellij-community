// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events

import com.intellij.util.text.nullize
import org.intellij.lang.annotations.Language

object AssertionParser {

  @JvmStatic
  fun parse(assertionMessage: String): Result? {
    return null
           // JUnit5: org.junit.jupiter.api.Assertions
           ?: parse(assertionMessage, "((?<message>.+) ==> )?expected: <(?<expected>.*)> but was: <(?<actual>.*)>")
           ?: parse(assertionMessage, "((?<message>.+) ==> )?expected: (?<expected>[^<]+<(.*)>) but was: (?<actual>[^<]+<(.*)>)")
           // JUnit 4: org.junit.Assert | junit.framework.Assert | junit.framework.TestCase | org.testng.AssertJUnit
           ?: parse(assertionMessage, "((?<message>.+) )?expected:<(?<expected>.*)> but was:<(?<actual>.*)>")
           ?: parse(assertionMessage, "((?<message>.+) )?expected: (?<expected>[^<]+<.*>) but was: (?<actual>[^<]+<.*>)")
           // AssertJ: org.assertj.core.api.Assertions
           ?: parse(assertionMessage, "(?<message>)\nexpected: \"(?<expected>.*)\"\n but was: \"(?<actual>.*)\"")
           ?: parse(assertionMessage, "(?<message>)\nexpected: (?<expected>.*)\n but was: (?<actual>.*)")
           // Test NG: org.testng.Assert
           ?: parse(assertionMessage, "((?<message>.+) )?expected \\[(?<expected>.*)] but found \\[(?<actual>.*)]")
  }

  private fun parse(assertionMessage: String, @Language("Regexp") rawRegex: String): Result? {
    val regex = rawRegex.toRegex(RegexOption.DOT_MATCHES_ALL)
    val matchesResult = regex.findAll(assertionMessage).firstOrNull() ?: return null
    if (matchesResult.groupValues.firstOrNull() != assertionMessage) {
      return null
    }
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