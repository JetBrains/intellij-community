package com.intellij.compose.ide.plugin.shared.highlighting

abstract class ComposableFunctionParameterCallHighlightingTestCase : BaseComposableCallHighlightingTestCase() {
  override val testDataSubdirectory: String = "composableFunctionParameter"

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