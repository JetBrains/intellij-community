// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection

import com.intellij.junit.testFramework.JUnitLibrary
import com.intellij.jvm.analysis.testFramework.JvmLanguage

abstract class KotlinJUnitMalformedDeclarationInspectionTestV57 : KotlinJUnitMalformedDeclarationInspectionTestBase(JUnitLibrary.JUNIT5_7_0) {
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