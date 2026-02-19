// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared

import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

abstract class ComposableNamingInspectionSuppressorTest : KotlinLightCodeInsightFixtureTestCase(), ExpectedPluginModeProvider {
  private val suppressor = ComposableNamingInspectionSuppressor()

  fun testDoSuppressFunctionNameForComposable() = testKotlin("FunctionName", expectedSuppressed = true) {
    """
      package test
      
      import androidx.compose.runtime.Composable
      
      @Composable
      fun My<caret>Fun() {}
    """.trimIndent()
  }

  fun testDoSuppressTestFunctionNameForComposable() = testKotlin("TestFunctionName", expectedSuppressed = true) {
    """
      package test
      
      import androidx.compose.runtime.Composable
      
      @Composable
      fun My<caret>Fun() {}
    """.trimIndent()
  }

  fun testDoNotSuppressOtherToolForComposable() = testKotlin("OtherTool", expectedSuppressed = false) {
    """
      package test
      
      import androidx.compose.runtime.Composable
      
      @Composable
      fun My<caret>Fun() {}
    """.trimIndent()
  }

  fun testDoNotSuppressFunctionNameForNonComposable() = testKotlin("FunctionName", expectedSuppressed = false) {
    """
      package test
      
      annotation class MyComposable
                  
      @MyComposable
      fun My<caret>Fun() {}
    """.trimIndent()
  }

  fun testDoNotSuppressTestFunctionNameForNonComposable() = testKotlin("TestFunctionName", expectedSuppressed = false) {
    """
      package test
      
      annotation class MyComposable
                  
      @MyComposable
      fun My<caret>Fun() {}
    """.trimIndent()
  }

  fun testDoNotSuppressFunctionNameForComposableInJava() = testJava("FunctionName") {
    """
      package test;
      
      import androidx.compose.runtime.Composable;
      
      class test {
        @Composable
        void My<caret>Fun() {}
      }
    """.trimIndent()
  }

  fun testDoNotSuppressTestFunctionNameForComposableInJava() = testJava("TestFunctionName") {
    """
      package test;
        
      import androidx.compose.runtime.Composable;
        
      class test {
        @Composable
        void My<caret>Fun() {}
      }
    """.trimIndent()
  }

  @OptIn(KaAllowAnalysisOnEdt::class)
  private fun test(
    toolId: String,
    expectedSuppressed: Boolean,
    configure: () -> PsiFile,
  ) = allowAnalysisOnEdt {
    // Prepare
    myFixture.addFileToProject(
      "Composable.kt",
      """
        package androidx.compose.runtime

        annotation class Composable
      """.trimIndent()
    )
    val file = configure()

    // Do
    val isSuppressed = suppressor.isSuppressedFor(file.findElementAt(editor.caretModel.offset)!!, toolId)

    // Check
    assertEquals(
      if (expectedSuppressed) {
        "$toolId inspection is expected to be suppressed for this element, however it is not"
      }
      else {
        "$toolId inspection is expected to be NOT suppressed for this element, however it is"
      },
      expectedSuppressed,
      isSuppressed
    )
  }

  private fun testKotlin(toolId: String, expectedSuppressed: Boolean, code: () -> String) =
    test(toolId = toolId, expectedSuppressed = expectedSuppressed) { myFixture.configureByText("test.kt", code()) }

  private fun testJava(toolId: String, code: () -> String) =
    test(toolId = toolId, expectedSuppressed = false) { myFixture.configureByText("test.java", code()) }
}