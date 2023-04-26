// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test

import org.jetbrains.plugins.gradle.execution.test.runner.events.AssertionParser
import org.junit.Test
import org.junit.jupiter.api.Assertions

class AssertionParserTest {

  @Test
  fun `parse comparison message test`() {
    // JUnit 4: assertEquals: org.junit.Assert | junit.framework.Assert | junit.framework.TestCase | org.testng.AssertJUnit
    assertComparisonParsing("expected:<1> but was:<2>", "1", "2")
    assertComparisonParsing("expected:<expected> but was:<actual>", "expected", "actual")
    assertComparisonParsing("expected:<<expected>> but was:<<actual>>", "<expected>", "<actual>")
    assertComparisonParsing("expected:<[expected]> but was:<[actual]>", "[expected]", "[actual]")
    assertComparisonParsing("expected:<exp\nected> but was:<a\nctual>", "exp\nected", "a\nctual")
    assertComparisonParsing("expected:<exp\\nected> but was:<act\\nual>", "exp\\nected", "act\\nual")
    assertComparisonParsing("expected:<\nexpected\n> but was:<\nactual\n>", "\nexpected\n", "\nactual\n")
    assertComparisonParsing("expected:< expected > but was:< actual >", " expected ", " actual ")
    assertComparisonParsing("assertion message expected:<expected> but was:<actual>", "expected", "actual", "assertion message")
    assertComparisonParsing("expected: org.example.Class<string> but was: org.example.Class<string>",
                            "org.example.Class<string>", "org.example.Class<string>")

    // JUnit 4: assertSame: org.junit.Assert | junit.framework.Assert | junit.framework.TestCase | org.testng.AssertJUnit
    assertComparisonParsing("expected same:<expected> was not:<actual>", "expected", "actual")
    assertComparisonParsing("expected same:<<expected>> was not:<<actual>>", "<expected>", "<actual>")
    assertComparisonParsing("expected same:<[expected]> was not:<[actual]>", "[expected]", "[actual]")
    assertComparisonParsing("expected same:<exp\nected> was not:<a\nctual>", "exp\nected", "a\nctual")
    assertComparisonParsing("expected same:<exp\\nected> was not:<act\\nual>", "exp\\nected", "act\\nual")
    assertComparisonParsing("expected same:<\nexpected\n> was not:<\nactual\n>", "\nexpected\n", "\nactual\n")
    assertComparisonParsing("expected same:< expected > was not:< actual >", " expected ", " actual ")
    assertComparisonParsing("assertion message expected same:<expected> was not:<actual>", "expected", "actual", "assertion message")

    // JUnit5: assertEquals | assertSame: org.junit.jupiter.api.Assertions
    assertComparisonParsing("expected: <1> but was: <2>", "1", "2")
    assertComparisonParsing("expected: <expected> but was: <actual>", "expected", "actual")
    assertComparisonParsing("expected: <<expected>> but was: <<actual>>", "<expected>", "<actual>")
    assertComparisonParsing("expected: <[expected]> but was: <[actual]>", "[expected]", "[actual]")
    assertComparisonParsing("expected: <exp\nected> but was: <a\nctual>", "exp\nected", "a\nctual")
    assertComparisonParsing("expected: <exp\\nected> but was: <act\\nual>", "exp\\nected", "act\\nual")
    assertComparisonParsing("expected: <\nexpected\n> but was: <\nactual\n>", "\nexpected\n", "\nactual\n")
    assertComparisonParsing("expected: < expected > but was: < actual >", " expected ", " actual ")
    assertComparisonParsing("assertion message ==> expected: <expected> but was: <actual>", "expected", "actual", "assertion message")
    assertComparisonParsing("expected: org.example.Class@5a2d131d<string> but was: org.example.Class@14fc1f0<string>",
                            "org.example.Class@5a2d131d<string>", "org.example.Class@14fc1f0<string>")
    assertComparisonParsing("assertion message ==> expected: org.example.Class@5a2d131d<string> but was: org.example.Class@14fc1f0<string>",
                            "org.example.Class@5a2d131d<string>", "org.example.Class@14fc1f0<string>", "assertion message")

    // AssertJ: assertEquals: org.assertj.core.api.Assertions
    assertComparisonParsing("\nexpected: 1\n but was: 2", "1", "2")
    assertComparisonParsing("\nexpected: \"expected\"\n but was: \"actual\"", "expected", "actual")
    assertComparisonParsing("\nexpected: \"<expected>\"\n but was: \"<actual>\"", "<expected>", "<actual>")
    assertComparisonParsing("\nexpected: \"[expected]\"\n but was: \"[actual]\"", "[expected]", "[actual]")
    assertComparisonParsing("\nexpected: \"exp\nected\"\n but was: \"a\nctual\"", "exp\nected", "a\nctual")
    assertComparisonParsing("\nexpected: \"exp\\nected\"\n but was: \"act\\nual\"", "exp\\nected", "act\\nual")
    assertComparisonParsing("\nexpected: \"\nexpected\n\"\n but was: \"\nactual\n\"", "\nexpected\n", "\nactual\n")
    assertComparisonParsing("\nexpected: \" expected \"\n but was: \" actual \"", " expected ", " actual ")
    assertComparisonParsing("\nexpected: \"string (org.example.Class@31bcf236)\"\n but was: \"string (org.example.Class@4b3ed2f0)\"",
                            "string (org.example.Class@31bcf236)", "string (org.example.Class@4b3ed2f0)")

    // AssertJ: assertSame: org.assertj.core.api.Assertions
    assertComparisonParsing("\nExpecting actual:\n  actual\nand:\n  expected\nto refer to the same object", "expected", "actual")
    assertComparisonParsing("\nExpecting actual:\n  <actual>\nand:\n  <expected>\nto refer to the same object", "<expected>", "<actual>")
    assertComparisonParsing("\nExpecting actual:\n  [actual]\nand:\n  [expected]\nto refer to the same object", "[expected]", "[actual]")
    assertComparisonParsing("\nExpecting actual:\n  a\nctual\nand:\n  exp\nected\nto refer to the same object", "exp\nected", "a\nctual")
    assertComparisonParsing("\nExpecting actual:\n  act\\nual\nand:\n  exp\\nected\nto refer to the same object", "exp\\nected", "act\\nual")
    assertComparisonParsing("\nExpecting actual:\n  \nactual\n\nand:\n  \nexpected\n\nto refer to the same object", "\nexpected\n", "\nactual\n")
    assertComparisonParsing("\nExpecting actual:\n   actual \nand:\n   expected \nto refer to the same object", " expected ", " actual ")

    // Test NG: assertEquals | assertSame: org.testng.Assert
    assertComparisonParsing("expected [1] but found [2]", "1", "2")
    assertComparisonParsing("expected [expected] but found [actual]", "expected", "actual")
    assertComparisonParsing("expected [<expected>] but found [<actual>]", "<expected>", "<actual>")
    assertComparisonParsing("expected [[expected]] but found [[actual]]", "[expected]", "[actual]")
    assertComparisonParsing("expected [exp\nected] but found [a\nctual]", "exp\nected", "a\nctual")
    assertComparisonParsing("expected [exp\\nected] but found [act\\nual]", "exp\\nected", "act\\nual")
    assertComparisonParsing("expected [\nexpected\n] but found [\nactual\n]", "\nexpected\n", "\nactual\n")
    assertComparisonParsing("expected [ expected ] but found [ actual ]", " expected ", " actual ")
    assertComparisonParsing("assertion message expected [expected] but found [actual]", "expected", "actual", "assertion message")
  }

  private fun assertComparisonParsing(
    actualAssertionMessage: String,
    expectedParsedExpected: String,
    expectedParsedActual: String,
    expectedParsedMessage: String? = null
  ) {
    val result = AssertionParser.parse(actualAssertionMessage)
    Assertions.assertNotNull(result) {
      "Assertion isn't parsed in $actualAssertionMessage"
    }
    result!!
    Assertions.assertEquals(expectedParsedExpected, result.expected) {
      "Assertion expected value parsed incorrectly in $actualAssertionMessage"
    }
    Assertions.assertEquals(expectedParsedActual, result.actual) {
      "Assertion actual value parsed incorrectly in $actualAssertionMessage"
    }
    Assertions.assertEquals(expectedParsedMessage, result.message) {
      "Assertion message value parsed incorrectly in $actualAssertionMessage"
    }
  }
}
