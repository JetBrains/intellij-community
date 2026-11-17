// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection

import com.intellij.execution.junit.kotlin.kotlinJUnitProjectFixture
import com.intellij.junit.testFramework.MavenTestLib
import com.intellij.junit.testFramework.junitMalformedDeclarationInspectionFixture
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
abstract class KotlinJUnitMalformedDeclarationInspectionTestV57 {
  companion object {
    private val projectFixture = kotlinJUnitProjectFixture(MavenTestLib.JUNIT5_7_0)
  }

  protected val myFixture by junitMalformedDeclarationInspectionFixture(projectFixture)

  @Test
  fun `test malformed extension make public quickfix`() {
    myFixture.testQuickFix(
      JvmLanguage.KOTLIN, """
      class A {
        @org.junit.jupiter.api.extension.RegisterExtension
        val myRule<caret>5 = Rule5()
        class Rule5 { }
      }
    """.trimIndent(), """
      class A {
        @JvmField
        @org.junit.jupiter.api.extension.RegisterExtension
        val myRule5 = Rule5()
        class Rule5 { }
      }
    """.trimIndent(), "Fix 'myRule5' field signature", testPreview = true)
  }
}