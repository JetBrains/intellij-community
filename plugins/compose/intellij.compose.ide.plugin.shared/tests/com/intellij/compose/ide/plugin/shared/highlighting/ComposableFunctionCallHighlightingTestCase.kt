// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared.highlighting

abstract class ComposableFunctionCallHighlightingTestCase : BaseComposableCallHighlightingTestCase() {

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
