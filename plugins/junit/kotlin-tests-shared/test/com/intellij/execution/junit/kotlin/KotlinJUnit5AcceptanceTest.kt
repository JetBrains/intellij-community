// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin

import com.intellij.codeInsight.TestFrameworks
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.junit.testFramework.MavenTestLib
import com.intellij.junit.testFramework.junitMalformedDeclarationInspectionFixture
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiClassOwner
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@TestApplication
abstract class KotlinJUnit5AcceptanceTest {
  companion object {
    private val projectFixture = kotlinJUnitProjectFixture(MavenTestLib.JUNIT5)
  }

  protected val myFixture by junitMalformedDeclarationInspectionFixture(projectFixture)

  @Test
  fun testCompoundAnnotation(): Unit = timeoutRunBlocking {
    myFixture.addFileToProject("CombinedKotlinAnnotation.kt", """@org.junit.jupiter.api.Test
annotation class CombinedKotlinAnnotation""")
    val file = myFixture.configureByText("tests.kt", """
      class Tests {
        @CombinedKotlinAnnotation
        fun testExampleKotlinAnnotation() {}
      }
    """.trimIndent())

    readAction {
      Assertions.assertNotNull(TestFrameworks.detectFramework((file as PsiClassOwner).classes[0]))
    }
  }

  @Test
  fun testBracesInMethodName(): Unit = timeoutRunBlocking {
    val file = myFixture.configureByText("tests.kt", """
      class Tests {
         @org.junit.jupiter.api.Test
         fun `test wi<caret>th (in name)`() {}
      }
    """.trimIndent())
    Assertions.assertInstanceOf(PsiClassOwner::class.java, file)
    readAction {
      val testMethod = (file as PsiClassOwner).classes[0].methods[0]
      Assertions.assertEquals("test with (in name)()", JUnitConfiguration.Data.getMethodPresentation(testMethod))
    }
  }
}