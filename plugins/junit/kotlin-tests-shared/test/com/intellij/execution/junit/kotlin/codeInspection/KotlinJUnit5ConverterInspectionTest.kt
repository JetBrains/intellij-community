// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection

import com.intellij.junit.testFramework.JUnit5ConverterInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

abstract class KotlinJUnit5ConverterInspectionTest : JUnit5ConverterInspectionTestBase(), ExpectedPluginModeProvider {
  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
  }

  fun `test qualified conversion`() {
    myFixture.testQuickFix(
      JvmLanguage.KOTLIN, """
      import org.junit.Test
      import org.junit.Assert

      class Qual<caret>ified {
          @Test
          fun testMethodCall() {
              Assert.assertArrayEquals(arrayOf<Any>(), null)
              Assert.assertArrayEquals("message", arrayOf<Any>(), null)
              Assert.assertEquals("Expected", "actual")
              Assert.assertEquals("message", "Expected", "actual")
              Assert.fail()
              Assert.fail("")
          }

          @Test
          fun testMethodRef() {
              fun foo(param: (Boolean) -> Unit) = param(false)
              foo(Assert::assertTrue)
          }
      }
    """.trimIndent(), """
      import org.junit.jupiter.api.Test
      import org.junit.jupiter.api.Assertions

      class Qualified {
          @Test
          fun testMethodCall() {
              Assertions.assertArrayEquals(arrayOf<Any>(), null)
              Assertions.assertArrayEquals(arrayOf<Any>(), null, "message")
              Assertions.assertEquals("Expected", "actual")
              Assertions.assertEquals("Expected", "actual", "message")
              Assertions.fail()
              Assertions.fail("")
          }

          @Test
          fun testMethodRef() {
              fun foo(param: (Boolean) -> Unit) = param(false)
              foo(Assertions::assertTrue)
          }
      }
    """.trimIndent(), "Migrate to JUnit 5")
  }

  fun `test unqualified conversion`() {
    myFixture.testQuickFix(
      JvmLanguage.KOTLIN, """
      import org.junit.Test
      import org.junit.Assert.*

      class UnQual<caret>ified {
          @Test
          fun testMethodCall() {
              assertArrayEquals(arrayOf<Any>(), null)
              assertArrayEquals("message", arrayOf<Any>(), null)
              assertEquals("Expected", "actual")
              assertEquals("message", "Expected", "actual")
              fail()
              fail("")
          }

          @Test
          fun testMethodRef() {
              fun foo(param: (Boolean) -> Unit) = param(false)
              foo(::assertTrue)
          }
      }
    """.trimIndent(), """
      import org.junit.jupiter.api.Test
      import org.junit.jupiter.api.Assertions

      class UnQualified {
          @Test
          fun testMethodCall() {
              Assertions.assertArrayEquals(arrayOf<Any>(), null)
              Assertions.assertArrayEquals(arrayOf<Any>(), null, "message")
              Assertions.assertEquals("Expected", "actual")
              Assertions.assertEquals("Expected", "actual", "message")
              Assertions.fail()
              Assertions.fail("")
          }

          @Test
          fun testMethodRef() {
              fun foo(param: (Boolean) -> Unit) = param(false)
              foo(Assertions::assertTrue)
          }
      }
    """.trimIndent(), "Migrate to JUnit 5")
  }

  open fun `test remove public modifier`() {
    myFixture.testQuickFix(
      JvmLanguage.KOTLIN, """
      import org.junit.Test

      class Presen<caret>ter {
          @Test
          fun testJUnit4() {}
      
          @org.junit.jupiter.api.Test
          fun testJUnit5() {}
      }
    """.trimIndent(), """
      import org.junit.jupiter.api.Test

      class Presenter {
          @Test
          fun testJUnit4() {}
      
          @org.junit.jupiter.api.Test
          fun testJUnit5() {}
      }
    """.trimIndent(), "Migrate to JUnit 5")
  }

  fun `test expected on test annotation`() {
    myFixture.testQuickFixUnavailable(
      JvmLanguage.KOTLIN, """
      import org.junit.Assert.*
      import org.junit.jupiter.api.Test

      class ExpectedOn<caret>TestAnnotation {
          @Test
          fun testFirst() { }
      }
    """.trimIndent())
  }
}