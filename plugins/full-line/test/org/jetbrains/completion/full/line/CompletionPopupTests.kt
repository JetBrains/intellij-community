package org.jetbrains.completion.full.line

import org.jetbrains.completion.full.line.platform.tests.FullLineCompletionTestCase
import org.junit.jupiter.api.Assertions

class CompletionPopupTests : FullLineCompletionTestCase() {
  override fun getBasePath() = "testData/completion/strings"

  // ML-173
  fun `test suggestion matched with prefix`() = stepByStepByText("Python", "context<caret>", lookupShownAfter = true) {
    suggestions("context", "context another one", "context another one too") {
      Assertions.assertNull(firstOrNull { it.lookupString == "context" })
    }
  }
}
