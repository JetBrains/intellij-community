// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin

import com.intellij.junit.testFramework.MavenTestLib
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


@TestApplication
abstract class KotlinJUnitTestRunLineMarkerTest {
  companion object {
    private val projectFixture = kotlinJUnitProjectFixture(MavenTestLib.JUNIT5)
  }

  protected val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun testCustomAnnotation() {
    myFixture.configureByText("MySuperTest.kt", """
      @Retention(AnnotationRetention.RUNTIME)
      @Target(AnnotationTarget.FUNCTION)
      @org.junit.jupiter.api.Test
      annotation class MySuperTest
      
      """.trimIndent())
    myFixture.configureByText("MyKTest.kt", """
      class MyKTest {
          @MySuperTest
          fun test<caret>1() {}
          @org.junit.jupiter.api.Test
          fun test2() {}
      }
      
      """.trimIndent())
    val gutters = myFixture.findGuttersAtCaret()
    val marks = runReadActionBlocking { gutters.filter { it.getTooltipText() == "Run Test" } }
    assertEquals(1, marks.size)
  }
}