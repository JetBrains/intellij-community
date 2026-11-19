// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection

import com.intellij.execution.junit.kotlin.kotlinJUnitProjectFixture
import com.intellij.junit.testFramework.MavenTestLib.JUNIT5
import com.intellij.junit.testFramework.junitAssertEqualsOnArrayInspectionFixture
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
abstract class KotlinJUnitAssertEqualsOnArrayInspectionTest {
  companion object {
    private val projectFixture = kotlinJUnitProjectFixture(JUNIT5)
  }

  protected val myFixture by junitAssertEqualsOnArrayInspectionFixture(projectFixture)

  @Test
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

  @Test
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