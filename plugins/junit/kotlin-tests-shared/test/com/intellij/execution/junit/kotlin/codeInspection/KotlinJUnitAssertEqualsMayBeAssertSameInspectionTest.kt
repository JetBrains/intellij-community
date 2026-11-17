// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection

import com.intellij.junit.testFramework.addJUnit4Library
import com.intellij.junit.testFramework.junitAssertEqualsMayBeAssertSameInspectionFixture
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.kotlin.idea.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.junit.jupiter.api.Test

private val ktProjectDescriptor = object : KotlinWithJdkAndRuntimeLightProjectDescriptor(
  listOf(TestKotlinArtifacts.kotlinStdlib), listOf(TestKotlinArtifacts.kotlinStdlibSources)
) {
  override fun configureModule(module: Module, model: ModifiableRootModel) {
    super.configureModule(module, model)
    model.addJUnit4Library()
  }
}

@TestApplication
abstract class KotlinJUnitAssertEqualsMayBeAssertSameInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(ktProjectDescriptor)
  }

  protected val myFixture by junitAssertEqualsMayBeAssertSameInspectionFixture(projectFixture)

  

  @Test
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

  @Test
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

  @Test
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

  @Test
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