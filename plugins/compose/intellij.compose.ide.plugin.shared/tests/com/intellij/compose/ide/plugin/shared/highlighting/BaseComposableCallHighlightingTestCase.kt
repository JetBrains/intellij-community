// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.compose.ide.plugin.shared.util.enableComposeInTest
import com.intellij.compose.ide.plugin.shared.util.isComposeEnabled
import com.intellij.compose.ide.plugin.shared.util.resolveTestDataDirectory
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import java.io.File

abstract class BaseComposableCallHighlightingTestCase  : KotlinLightCodeInsightFixtureTestCase(), ExpectedPluginModeProvider {

  override val testDataDirectory: File
    get() = resolveTestDataDirectory("testData/highlighting/$testDataSubdirectory").toFile()

  /**
   * Test data subdirectory in the 'testData/highlighting' test dir.
   */
  abstract val testDataSubdirectory: String

  /**
   * Returns a [HighlightInfoType] of an element under the carrot in a given [PsiFile] file.
   */
  abstract fun PsiFile.highlightCallUnderCaret(): HighlightInfoType?

  protected fun doTestHighlightingWithEnabledCompose(testFileToHighlight: PsiFile, expectedHighlightingResult: HighlightInfoType?) {
    myFixture.enableComposeInTest()

    val actualHighlightingInfoType = testFileToHighlight.highlightCallUnderCaret()

    assertEquals(
      "Highlight is expected to be $expectedHighlightingResult, but it was ${actualHighlightingInfoType}.",
      expectedHighlightingResult,
      actualHighlightingInfoType
    )
  }

  protected fun doTestHighlightingWithDisabledCompose(testFileToHighlight: PsiFile) {
    if (myFixture.isComposeEnabled()) fail("Compose is enabled in test when it is expected to be disabled.")

    val actualHighlightingInfoType = testFileToHighlight.highlightCallUnderCaret()

    assertEquals(
      "Highlight is expected to be 'null', but it was ${actualHighlightingInfoType}.",
      null,
      actualHighlightingInfoType
    )
  }
}