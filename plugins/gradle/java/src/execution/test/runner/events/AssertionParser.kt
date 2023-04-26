// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events

import com.intellij.util.text.nullize
import org.intellij.lang.annotations.Language

object AssertionParser {

  @JvmStatic
  fun parse(assertionMessage: String): Result? {
    return null
           // JUnit 5: assertEquals | assertSame: org.junit.jupiter.api.Assertions
           ?: parse(assertionMessage, "((?<message>.+) ==> )?expected: <(?<expected>.*)> but was: <(?<actual>.*)>")
           ?: parse(assertionMessage, "((?<message>.+) ==> )?expected: (?<expected>[^<]+<(.*)>) but was: (?<actual>[^<]+<(.*)>)")
           // JUnit 4: assertEquals: org.junit.Assert | junit.framework.Assert | junit.framework.TestCase | org.testng.AssertJUnit
           ?: parse(assertionMessage, "((?<message>.+) )?expected:<(?<expected>.*)> but was:<(?<actual>.*)>")
           ?: parse(assertionMessage, "((?<message>.+) )?expected: (?<expected>[^<]+<.*>) but was: (?<actual>[^<]+<.*>)")
           // JUnit 4: assertSame: org.junit.Assert | junit.framework.Assert | junit.framework.TestCase | org.testng.AssertJUnit
           ?: parse(assertionMessage, "((?<message>.+) )?expected same:<(?<expected>.*)> was not:<(?<actual>.*)>")
           // AssertJ: assertEquals: org.assertj.core.api.Assertions
           ?: parse(assertionMessage, "(?<message>)\nexpected: (?<expected>.*)\n but was: (?<actual>.*)")
           // AssertJ: assertSame: org.assertj.core.api.Assertions
           ?: parse(assertionMessage, "(?<message>)\nExpecting actual:\n {2}(?<actual>.*)\nand:\n {2}(?<expected>.*)\nto refer to the same object")
           // Test NG: assertEquals | assertSame: org.testng.Assert
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