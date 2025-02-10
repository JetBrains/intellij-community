package com.intellij.compose.ide.plugin.shared.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.compose.ide.plugin.shared.util.enableComposeInTest
import com.intellij.compose.ide.plugin.shared.util.isComposeEnabled
import com.intellij.compose.ide.plugin.shared.util.resolveTestDataDirectory
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import java.io.File

abstract class ComposeFunctionCallHighlightingTestCase : KotlinLightCodeInsightFixtureTestCase(), ExpectedPluginModeProvider {

  override val testDataDirectory: File
    get() = resolveTestDataDirectory("testData/highlighting/composableFunction").toFile()

  /**
   * Returns a [HighlightInfoType] of an element under the carrot in a given [PsiFile] file.
   */
  abstract fun PsiFile.highlightCallUnderCaret(): HighlightInfoType?

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

  private fun doTestHighlightingWithEnabledCompose(testFileToHighlight: PsiFile, expectedHighlightingResult: HighlightInfoType?) {
    myFixture.enableComposeInTest()

    val actualHighlightingInfoType = testFileToHighlight.highlightCallUnderCaret()

    assertEquals(
      "Highlight is expected to be $expectedHighlightingResult, but it was ${actualHighlightingInfoType}.",
      expectedHighlightingResult,
      actualHighlightingInfoType
    )
  }

  private fun doTestHighlightingWithDisabledCompose(testFileToHighlight: PsiFile) {
    if (myFixture.isComposeEnabled()) fail("Compose is enabled in test when it is expected to be disabled.")

    val actualHighlightingInfoType = testFileToHighlight.highlightCallUnderCaret()

    assertEquals(
      "Highlight is expected to be 'null', but it was ${actualHighlightingInfoType}.",
      null,
      actualHighlightingInfoType
    )
  }
}
