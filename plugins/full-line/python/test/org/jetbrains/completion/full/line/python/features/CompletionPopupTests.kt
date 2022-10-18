package org.jetbrains.completion.full.line.python.features

import org.jetbrains.completion.full.line.python.tests.FullLinePythonCompletionTestCase
import org.junit.jupiter.api.Assertions

class CompletionPopupTests : FullLinePythonCompletionTestCase() {
  override fun getBasePath() = "testData/completion/strings"

  // ML-173
  fun `test suggestion matched with prefix`() = stepByStepByText("Python", "context<caret>", lookupShownAfter = true) {
    suggestions("context", "context another one", "context another one too") {
      Assertions.assertNull(firstOrNull { it.lookupString == "context" })
    }
  }
}
