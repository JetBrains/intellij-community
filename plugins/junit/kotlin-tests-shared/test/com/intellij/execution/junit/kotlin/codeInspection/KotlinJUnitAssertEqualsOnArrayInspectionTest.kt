// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection

import com.intellij.junit.testFramework.JUnitAssertEqualsOnArrayInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

abstract class KotlinJUnitAssertEqualsOnArrayInspectionTest : JUnitAssertEqualsOnArrayInspectionTestBase(), ExpectedPluginModeProvider {
  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
  }

  fun `test highlighting`() {
    myFixture.testHighlighting(
      JvmLanguage.KOTLIN, """
      import org.junit.jupiter.api.Assertions
      
      class MyTest {
          fun myTest() {
              val a = arrayOf<Any>()
              val e = arrayOf<String>("")
              Assertions.<warning descr="'assertEquals()' called on array">assertEquals</warning>(a, e, "message")
          }
      }      
    """.trimIndent())
  }

  fun `test quickfix`() {
    myFixture.testQuickFix(
      JvmLanguage.KOTLIN, """
      import org.junit.jupiter.api.Assertions
      
      class MyTest {
          fun myTest() {
              val a = arrayOf<Any>()
              val e = arrayOf<String>("")
              Assertions.assert<caret>Equals(a, e, "message")
          }
      }
    """.trimIndent(), """
      import org.junit.jupiter.api.Assertions
      
      class MyTest {
          fun myTest() {
              val a = arrayOf<Any>()
              val e = arrayOf<String>("")
              Assertions.assertArrayEquals(a, e, "message")
          }
      }
    """.trimIndent(), "Replace with 'assertArrayEquals()'")
  }
}