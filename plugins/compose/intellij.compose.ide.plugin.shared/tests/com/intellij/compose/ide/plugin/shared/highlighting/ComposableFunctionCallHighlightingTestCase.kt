package com.intellij.compose.ide.plugin.shared.highlighting

abstract class ComposableFunctionCallHighlightingTestCase : BaseComposableCallHighlightingTestCase() {

  override val testDataSubdirectory: String
    get() = "composableFunction"

  fun `test Composable function call within Composable function with Compose enable`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableFunctionCallWithinComposableFunctionBody.kt")

    doTestHighlightingWithEnabledCompose(testFileToHighlight, COMPOSABLE_CALL_TEXT_TYPE)
  }

  fun `test Composable function call within Composable function with Compose disabled`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableFunctionCallWithinComposableFunctionBody.kt")

    doTestHighlightingWithDisabledCompose(testFileToHighlight)
  }

  fun `test Composable function call within non-Composable function with Compose enabled`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableFunctionCallWithinNonComposableFunctionBody.kt")

    doTestHighlightingWithEnabledCompose(testFileToHighlight, COMPOSABLE_CALL_TEXT_TYPE)
  }

  fun `test Composable function call within non-Composable function with Compose disabled`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableFunctionCallWithinNonComposableFunctionBody.kt")

    doTestHighlightingWithDisabledCompose(testFileToHighlight)
  }

  fun `test non-Composable function call within non-Composable function with Compose enabled`() {
    val testFileToHighlight = myFixture.configureByFile("testNonComposableFunctionCallWithinNonComposableFunctionBody.kt")

    doTestHighlightingWithEnabledCompose(testFileToHighlight, null)
  }

  fun `test non-Composable function call within non-Composable function with Compose disabled`() {
    val testFileToHighlight = myFixture.configureByFile("testNonComposableFunctionCallWithinNonComposableFunctionBody.kt")

    doTestHighlightingWithDisabledCompose(testFileToHighlight)
  }

  fun `test Composable function call within Composable class member function with Compose enabled`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableFunctionCallWithinClassMemberComposableFunctionBody.kt")

    doTestHighlightingWithDisabledCompose(testFileToHighlight)
  }

  fun `test Composable function call within Composable class member function with Compose disabled`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableFunctionCallWithinClassMemberComposableFunctionBody.kt")

    doTestHighlightingWithDisabledCompose(testFileToHighlight)
  }
}
