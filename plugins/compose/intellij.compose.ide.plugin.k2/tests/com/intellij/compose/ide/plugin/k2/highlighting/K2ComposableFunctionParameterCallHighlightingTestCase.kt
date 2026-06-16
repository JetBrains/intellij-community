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

internal class K2ComposableFunctionParameterCallHighlightingTestCase : K2BaseComposableCallHighlightingTestCase() {
  private val ext = K2ComposableFunctionCallHighlighterExtension()

  override val testDataSubdirectory: String = "composableFunctionParameter"

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

  fun `test Composable function Lambda argument invoke operator call within Composable function with Compose enable`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableLambdaFunctionParameterInvokeOperatorCallInComposableFunction.kt")

    doTestHighlightingWithEnabledCompose(testFileToHighlight, COMPOSABLE_CALL_TEXT_TYPE)
  }

  fun `test Composable function Lambda argument invoke operator call within Composable function with Compose disabled`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableLambdaFunctionParameterInvokeOperatorCallInNonComposableFunction.kt")

    doTestHighlightingWithDisabledCompose(testFileToHighlight)
  }

  fun `test Composable function Lambda argument invoke method call within Composable function with Compose enable`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableLambdaFunctionParameterInvokeMethodCallInComposableFunction.kt")

    doTestHighlightingWithEnabledCompose(testFileToHighlight, COMPOSABLE_CALL_TEXT_TYPE)
  }

  fun `test Composable function Lambda argument invoke method call within Composable function with Compose disabled`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableLambdaFunctionParameterInvokeMethodCallInNonComposableFunction.kt")

    doTestHighlightingWithDisabledCompose(testFileToHighlight)
  }

  fun `test Composable function Lambda argument toString call within Composable function with Compose enabled`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableLambdaFunctionParameterToStringMethodCallInComposableFunction.kt")

    doTestHighlightingWithEnabledCompose(testFileToHighlight, null)
  }

  fun `test Composable function Lambda argument toString call within Composable function with Compose disabled`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableLambdaFunctionParameterToStringMethodCallInNonComposableFunction.kt")

    doTestHighlightingWithDisabledCompose(testFileToHighlight)
  }

  fun `test Composable function Lambda argument reference call within Composable function with Compose enabled`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableFunctionParameterReferenceCallInComposableFunction.kt")

    doTestHighlightingWithEnabledCompose(testFileToHighlight, null)
  }

  fun `test Composable function Lambda argument reference call within Composable function with Compose disabled`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableFunctionParameterReferenceCallInNonComposableFunction.kt")

    doTestHighlightingWithDisabledCompose(testFileToHighlight)
  }
}