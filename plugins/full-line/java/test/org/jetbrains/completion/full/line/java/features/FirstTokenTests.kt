package org.jetbrains.completion.full.line.java.features

import org.jetbrains.completion.full.line.platform.tests.FullLineCompletionTestCase

class FirstTokenTests : FullLineCompletionTestCase() {
  override fun getBasePath() = "testData/completion/features/first-token"

  fun `test InsertionAfterNew`() = doTabTest("JAVA", "new String")
}
