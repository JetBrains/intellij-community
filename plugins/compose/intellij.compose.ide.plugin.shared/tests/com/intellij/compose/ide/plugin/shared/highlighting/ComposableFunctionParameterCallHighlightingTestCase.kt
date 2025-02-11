package com.intellij.compose.ide.plugin.shared.highlighting

abstract class ComposableFunctionParameterCallHighlightingTestCase : BaseComposableCallHighlightingTestCase() {
  override val testDataSubdirectory: String = "composableFunctionParameter"

  fun `test Composable function Lambda argument invoke operator call within Composable function with Compose enable`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableLambdaFunctionParameterCallInComposableFunction.kt")

    doTestHighlightingWithEnabledCompose(testFileToHighlight, COMPOSABLE_CALL_TEXT_TYPE)
  }

  fun `test Composable function Lambda argument invoke operator call within Composable function with Compose disabled`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableLambdaFunctionParameterCallInNonComposableFunction.kt")

    doTestHighlightingWithDisabledCompose(testFileToHighlight)
  }

  fun `test Composable function Lambda argument call within Composable function with Compose enable`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableLambdaFunctionParameterInvokeMethodCallInComposableFunction.kt")

    doTestHighlightingWithEnabledCompose(testFileToHighlight, COMPOSABLE_CALL_TEXT_TYPE)
  }

  fun `test Composable function Lambda argument call within Composable function with Compose disabled`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableLambdaFunctionParameterInvokeMethodCallInNonComposableFunction.kt")

    doTestHighlightingWithDisabledCompose(testFileToHighlight)
  }
}