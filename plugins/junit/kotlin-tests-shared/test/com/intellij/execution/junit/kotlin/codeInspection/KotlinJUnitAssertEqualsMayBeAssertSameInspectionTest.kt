// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection

import com.intellij.junit.testFramework.JUnitAssertEqualsMayBeAssertSameInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

abstract class KotlinJUnitAssertEqualsMayBeAssertSameInspectionTest : JUnitAssertEqualsMayBeAssertSameInspectionTestBase(), ExpectedPluginModeProvider {
  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
  }

  fun `test JUnit 3 highlighting`() {
    myFixture.testHighlighting(
      JvmLanguage.KOTLIN, """
      class Test : junit.framework.TestCase() { 
          fun testOne() { 
              <warning descr="'assertEquals()' may be 'assertSame()'">assertEquals</warning>(A.a, A.b)
          } 
      }
    """.trimIndent())
  }

  fun `test JUnit 3 quickfix`() {
    myFixture.testQuickFix(
      JvmLanguage.KOTLIN, """
      class Test : junit.framework.TestCase() { 
          fun testOne() {
              asser<caret>tEquals(A.a, A.b)
          } 
      }
    """.trimIndent(), """
      class Test : junit.framework.TestCase() { 
          fun testOne() {
              assertSame(A.a, A.b)
          } 
      }
    """.trimIndent(), "Replace with 'assertSame()'")
  }

  fun `test JUnit 4 highlighting`() {
    myFixture.testHighlighting(
      JvmLanguage.KOTLIN, """
      class Test { 
          @org.junit.Test 
          fun test() { 
              org.junit.Assert.<warning descr="'assertEquals()' may be 'assertSame()'">assertEquals</warning>(A.a, A.b)
          } 
      }
    """.trimIndent())
  }

  fun `test JUnit 4 quickfix`() {
    myFixture.testQuickFix(
      JvmLanguage.KOTLIN, """
      class Test { 
          @org.junit.Test 
          fun test() {
              org.junit.Assert.assert<caret>Equals(A.a, A.b)
          } 
      }
    """.trimIndent(), """
      import org.junit.Assert
      
      class Test { 
          @org.junit.Test 
          fun test() {
              Assert.assertSame(A.a, A.b)
          } 
      }
    """.trimIndent(), "Replace with 'assertSame()'")
  }
}