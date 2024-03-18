// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.junit.testFramework.JUnit5AssertionsConverterInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class KotlinJUnit5AssertionsConverterInspectionTest : JUnit5AssertionsConverterInspectionTestBase() {
  fun `test AssertArrayEquals`() {
    myFixture.testQuickFix(
      JvmLanguage.KOTLIN, """
      import org.junit.Assert.*
      import org.junit.jupiter.api.Test

      class Test1 {
          @Test
          fun testFirst() {
              assert<caret>ArrayEquals(arrayOfNulls<Any>(0), null)
          }
      }
    """.trimIndent(), """
      import org.junit.Assert.*
      import org.junit.jupiter.api.Assertions
      import org.junit.jupiter.api.Test

      class Test1 {
          @Test
          fun testFirst() {
              Assertions.assertArrayEquals(arrayOfNulls<Any>(0), null)
          }
      }
    """.trimIndent(), "Replace with 'org.junit.jupiter.api.Assertions' method call")
  }

  fun `test AssertArrayEquals message`() {
    myFixture.testQuickFix(
      JvmLanguage.KOTLIN, """
      import org.junit.Assert.*
      import org.junit.jupiter.api.Test

      class Test1 {
          @Test
          fun testFirst() {
            assert<caret>ArrayEquals("message", arrayOfNulls<Any>(0), null)
          }
      }
    """.trimIndent(), """
      import org.junit.Assert.*
      import org.junit.jupiter.api.Assertions
      import org.junit.jupiter.api.Test

      class Test1 {
          @Test
          fun testFirst() {
              Assertions.assertArrayEquals(arrayOfNulls<Any>(0), null, "message")
          }
      }
    """.trimIndent(), "Replace with 'org.junit.jupiter.api.Assertions' method call")
  }

  fun `test AssertEquals`() {
    myFixture.testQuickFix(
      JvmLanguage.KOTLIN, """
      import org.junit.Assert.*
      import org.junit.jupiter.api.Test

      class Test1 {
          @Test
          fun testFirst() {
              assert<caret>Equals("message", "Expected", "actual")
          }
      }
    """.trimIndent(), """
      import org.junit.Assert.*
      import org.junit.jupiter.api.Assertions
      import org.junit.jupiter.api.Test

      class Test1 {
          @Test
          fun testFirst() {
              Assertions.assertEquals("Expected", "actual", "message")
          }
      }
    """.trimIndent(), "Replace with 'org.junit.jupiter.api.Assertions' method call")
  }

  fun `test AssertNotEqualsWithDelta`() {
    myFixture.testQuickFixUnavailable(
      JvmLanguage.KOTLIN, """
      import org.junit.Assert.*
      import org.hamcrest.Matcher;
      import org.junit.jupiter.api.Test

      class Test1 {
          @Test
          fun testFirst() {
              assertNotEquals(1.0, 1.0, 1.0);
          }
      }
    """.trimIndent())
  }

  fun `test AssertThat`() {
    myFixture.testQuickFix(
      JvmLanguage.KOTLIN, """
      import org.junit.Assert.*
      import org.hamcrest.Matcher
      import org.junit.jupiter.api.Test

      internal class Test1 {
          @Test
          fun testFirst(matcher: Matcher<String>) {
              assert<caret>That ("reason", "null", matcher)
          }
      }
    """.trimIndent(), """
      import org.junit.Assert.*
      import org.hamcrest.Matcher
      import org.hamcrest.MatcherAssert
      import org.junit.jupiter.api.Test

      internal class Test1 {
          @Test
          fun testFirst(matcher: Matcher<String>) {
              MatcherAssert.assertThat("reason", "null", matcher)
          }
      }
    """.trimIndent(), "Replace with 'org.hamcrest.MatcherAssert' method call")
  }

  fun `test AssertTrue`() {
    myFixture.testQuickFix(
      JvmLanguage.KOTLIN, """
      import org.junit.Assert.*
      import org.junit.jupiter.api.Test

      class Test1 {
          @Test
          fun testFirst() {
              assert<caret>True ("message", false)
          }
      }
    """.trimIndent(), """
      import org.junit.Assert.*
      import org.junit.jupiter.api.Assertions
      import org.junit.jupiter.api.Test

      class Test1 {
          @Test
          fun testFirst() {
              Assertions.assertTrue(false, "message")
          }
      }
    """.trimIndent(), "Replace with 'org.junit.jupiter.api.Assertions' method call")
  }

  fun `test AssertTrue method reference`() {
    myFixture.testQuickFix(
      JvmLanguage.KOTLIN, """
      import org.junit.Assert
      import org.junit.jupiter.api.Test

      class Test1 {
          @Test
          fun testFirst() {
              someFun(Assert::assert<caret>True)
          }

          fun someFun(param: (Boolean) -> Unit) {
              param(false)
          }
      }
    """.trimIndent(), """
      import org.junit.Assert
      import org.junit.jupiter.api.Assertions
      import org.junit.jupiter.api.Test

      class Test1 {
          @Test
          fun testFirst() {
              someFun(Assertions::assert<caret>True)
          }

          fun someFun(param: (Boolean) -> Unit) {
              param(false)
          }
      }
    """.trimIndent(), "Replace with 'org.junit.jupiter.api.Assertions' method call")
  }

  fun `test AssumeTrue`() {
    myFixture.testQuickFix(
      JvmLanguage.KOTLIN, """
      import org.junit.Assume.*
      import org.junit.jupiter.api.Test

      class Test1 {
          @Test
          fun testFirst() {
              assume<caret>True ("message", false)
          }
      }
    """.trimIndent(), """
      import org.junit.Assume.*
      import org.junit.jupiter.api.Assumptions
      import org.junit.jupiter.api.Test

      class Test1 {
          @Test
          fun testFirst() {
              Assumptions.assumeTrue(false, "message")
          }
      }
    """.trimIndent(), "Replace with 'org.junit.jupiter.api.Assumptions' method call")
  }
}