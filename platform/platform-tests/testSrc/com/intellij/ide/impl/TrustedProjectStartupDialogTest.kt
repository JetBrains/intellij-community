// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.ide.trustedProjects.impl.TrustedProjectStartupDialog
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@TestApplication
@SystemProperty("idea.trust.headless.disabled", "false")
internal class TrustedProjectStartupDialogTest {
  private val testRoot by tempPathFixture()

  @Test
  @SystemProperty(TrustedProjectStartupDialog.TRUSTED_PROJECT_DIALOG_HAS_CANCEL_BUTTON_KEY, "false")
  fun `trust project startup dialog ignores implicit cancel when cancel button is disabled`() = runBlocking(Dispatchers.EDT) {
    val dialog = createStartupDialog()
    try {
      val buttonTexts = getButtonTexts(dialog)

      Assertions.assertTrue(buttonTexts.contains(TRUST_BUTTON_TEXT))
      Assertions.assertTrue(buttonTexts.contains(DISTRUST_BUTTON_TEXT))
      Assertions.assertFalse(buttonTexts.contains(CANCEL_BUTTON_TEXT))
      Assertions.assertFalse(dialog.hasImplicitCancelActionInTests())
      Assertions.assertFalse(dialog.shouldCloseOnCross())
    }
    finally {
      dialog.disposeIfNeeded()
    }
  }

  @Test
  @SystemProperty(TrustedProjectStartupDialog.TRUSTED_PROJECT_DIALOG_HAS_CANCEL_BUTTON_KEY, "true")
  fun `trust project startup dialog supports implicit cancel when cancel button is enabled`() = runBlocking(Dispatchers.EDT) {
    val dialog = createStartupDialog()
    try {
      val buttonTexts = getButtonTexts(dialog)

      Assertions.assertTrue(buttonTexts.contains(TRUST_BUTTON_TEXT))
      Assertions.assertTrue(buttonTexts.contains(DISTRUST_BUTTON_TEXT))
      Assertions.assertTrue(buttonTexts.contains(CANCEL_BUTTON_TEXT))
      Assertions.assertTrue(dialog.hasImplicitCancelActionInTests())
      Assertions.assertTrue(dialog.shouldCloseOnCross())
    }
    finally {
      dialog.disposeIfNeeded()
    }
  }

  private fun createStartupDialog(): TrustedProjectStartupDialog {
    return TrustedProjectStartupDialog.createDialogInTests(
      project = null,
      projectPath = testRoot.resolve("project"),
      title = "Trust Project?",
      message = "Do you trust this project?",
      trustButtonText = TRUST_BUTTON_TEXT,
      distrustButtonText = DISTRUST_BUTTON_TEXT,
      cancelButtonText = CANCEL_BUTTON_TEXT,
    )
  }

  private fun getButtonTexts(dialog: TrustedProjectStartupDialog): Set<String> {
    return dialog.getButtonTextsInTests()
  }

  private companion object {
    private const val TRUST_BUTTON_TEXT = "Trust"
    private const val DISTRUST_BUTTON_TEXT = "Safe Mode"
    private const val CANCEL_BUTTON_TEXT = "Cancel"
  }
}
