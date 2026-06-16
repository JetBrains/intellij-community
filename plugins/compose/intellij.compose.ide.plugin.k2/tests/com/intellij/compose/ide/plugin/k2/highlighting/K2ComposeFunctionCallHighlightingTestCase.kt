// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.k2.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulCall
import org.jetbrains.kotlin.analysis.api.resolution.simple
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

internal class K2ComposeFunctionCallHighlightingTestCase : K2BaseComposableCallHighlightingTestCase() {
  private val ext = K2ComposableFunctionCallHighlighterExtension()

  @OptIn(KaAllowAnalysisOnEdt::class, KaExperimentalApi::class)
  override fun PsiFile.highlightCallUnderCaret(): HighlightInfoType? = allowAnalysisOnEdt {
    val element = checkNotNull(this.findElementAt(editor.caretModel.offset)) {
      "Element at caret not found!"
    }

    return analyze(file as KtFile) {
      val call = (element.parentOfType<KtCallExpression>() ?: element.parentOfType<KtNameReferenceExpression>())
        ?.resolveSuccessfulCall()?.simple

      checkNotNull(call) {
        "Call was not found!"
      }

      with(ext) {
        highlightCall(element, call)
      }
    }
  }

  override val testDataSubdirectory: String
    get() = "composableFunction"

  fun `test Composable function call within Composable function with Compose enable`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableCallInComposableFunction.kt")

    doTestHighlightingWithEnabledCompose(testFileToHighlight, COMPOSABLE_CALL_TEXT_TYPE)
  }

  fun `test Composable function call within Composable function with Compose disabled`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableCallInComposableFunction.kt")

    doTestHighlightingWithDisabledCompose(testFileToHighlight)
  }

  fun `test Composable function call within non-Composable function with Compose enabled`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableCallInNonComposableFunction.kt")

    doTestHighlightingWithEnabledCompose(testFileToHighlight, COMPOSABLE_CALL_TEXT_TYPE)
  }

  fun `test Composable function call within non-Composable function with Compose disabled`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableCallInNonComposableFunction.kt")

    doTestHighlightingWithDisabledCompose(testFileToHighlight)
  }

  fun `test non-Composable function call within non-Composable function with Compose enabled`() {
    val testFileToHighlight = myFixture.configureByFile("testNonComposableCallInNonComposableFunction.kt")

    doTestHighlightingWithEnabledCompose(testFileToHighlight, null)
  }

  fun `test non-Composable function call within non-Composable function with Compose disabled`() {
    val testFileToHighlight = myFixture.configureByFile("testNonComposableCallInNonComposableFunction.kt")

    doTestHighlightingWithDisabledCompose(testFileToHighlight)
  }

  fun `test non-Composable function call within Composable function with Compose enabled`() {
    val testFileToHighlight = myFixture.configureByFile("testNonComposableCallInComposableFunction.kt")

    doTestHighlightingWithEnabledCompose(testFileToHighlight, null)
  }

  fun `test non-Composable function call within Composable function with Compose disabled`() {
    val testFileToHighlight = myFixture.configureByFile("testNonComposableCallInComposableFunction.kt")

    doTestHighlightingWithDisabledCompose(testFileToHighlight)
  }

  fun `test Composable function call within Composable class member function with Compose enabled`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableCallInComposableMember.kt")

    doTestHighlightingWithEnabledCompose(testFileToHighlight, COMPOSABLE_CALL_TEXT_TYPE)
  }

  fun `test Composable function call within Composable class member function with Compose disabled`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableCallInComposableMember.kt")

    doTestHighlightingWithDisabledCompose(testFileToHighlight)
  }

  fun `test function with Composable return type call within Composable class member function with Compose enabled`() {
    val testFileToHighlight = myFixture.configureByFile("testFunctionWithComposableLambdaReturnTypeCallInComposableFunction.kt")

    doTestHighlightingWithEnabledCompose(testFileToHighlight, null)
  }

  fun `test function with Composable return type call within Composable class member function with Compose disabled`() {
    val testFileToHighlight = myFixture.configureByFile("testFunctionWithComposableLambdaReturnTypeCallInNonComposableFunction.kt")

    doTestHighlightingWithDisabledCompose(testFileToHighlight)
  }

  fun `test Composable return type invoke method call within Composable function with Compose enabled`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableLambdaReturnTypeInvokeMethodCallInComposableFunction.kt")

    doTestHighlightingWithEnabledCompose(testFileToHighlight, COMPOSABLE_CALL_TEXT_TYPE)
  }

  fun `test Composable return type invoke operator call within Composable function with Compose enabled`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableLambdaReturnTypeInvokeOperatorCallInComposableFunction.kt")

    doTestHighlightingWithEnabledCompose(testFileToHighlight, COMPOSABLE_CALL_TEXT_TYPE)
  }
}