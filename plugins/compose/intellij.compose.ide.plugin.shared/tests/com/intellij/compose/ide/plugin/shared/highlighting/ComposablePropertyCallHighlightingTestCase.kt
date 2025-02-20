package com.intellij.compose.ide.plugin.shared.highlighting

abstract class ComposablePropertyCallHighlightingTestCase : BaseComposableCallHighlightingTestCase() {

  override val testDataSubdirectory: String
    get() = "composableProperty"

  fun `test Composable property call within Composable function with Compose enable`() {
    val testFileToHighlight = myFixture.configureByFile("testComposablePropertyCallInComposableFunction.kt")

    doTestHighlightingWithEnabledCompose(testFileToHighlight, COMPOSABLE_CALL_TEXT_TYPE)
  }

  fun `test Composable property call within Composable function with Compose disabled`() {
    val testFileToHighlight = myFixture.configureByFile("testComposablePropertyCallInComposableFunction.kt")

    doTestHighlightingWithDisabledCompose(testFileToHighlight)
  }
  
  fun `test Class member Composable property call within Composable function with Compose enable`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableClassMemberPropertyCallInComposableFunction.kt")

    doTestHighlightingWithEnabledCompose(testFileToHighlight, COMPOSABLE_CALL_TEXT_TYPE)
  }

  fun `test Class member Composable property call within Composable function with Compose disabled`() {
    val testFileToHighlight = myFixture.configureByFile("testComposableClassMemberPropertyCallInComposableFunction.kt")

    doTestHighlightingWithDisabledCompose(testFileToHighlight)
  }
}