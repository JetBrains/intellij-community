// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin

import com.intellij.codeInsight.TestFrameworks
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.junit.testFramework.JUnitLibrary
import com.intellij.junit.testFramework.JUnitMalformedDeclarationInspectionTestBase
import com.intellij.psi.PsiClassOwner
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import org.junit.jupiter.api.Assertions

abstract class KotlinJUnit5AcceptanceTest : JUnitMalformedDeclarationInspectionTestBase(JUnitLibrary.JUNIT5), ExpectedPluginModeProvider {
  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
    ConfigLibraryUtil.configureKotlinRuntime(module)
  }

  fun testCompoundAnnotation() {
    myFixture.addFileToProject("CombinedKotlinAnnotation.kt","""@org.junit.jupiter.api.Test
annotation class CombinedKotlinAnnotation""")
    val file = myFixture.configureByText("tests.kt", """
      class Tests {
        @CombinedKotlinAnnotation
        fun testExampleKotlinAnnotation() {}
      }
    """.trimIndent())

    Assertions.assertNotNull(TestFrameworks.detectFramework((file as PsiClassOwner).classes[0]))
  }

  fun testBracesInMethodName() {
    val file = myFixture.configureByText("tests.kt", """
      class Tests {
         @org.junit.jupiter.api.Test
         fun `test wi<caret>th (in name)`() {}
      }
    """.trimIndent())
    Assertions.assertInstanceOf(PsiClassOwner::class.java, file)
    val testMethod = (file as PsiClassOwner).classes[0].methods[0]
    Assertions.assertEquals("test with (in name)()", JUnitConfiguration.Data.getMethodPresentation(testMethod))
  }
}