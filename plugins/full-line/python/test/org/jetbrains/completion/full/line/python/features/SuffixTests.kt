package org.jetbrains.completion.full.line.python.features

import org.jetbrains.completion.full.line.platform.tests.FullLineCompletionTestCase

class CaretSuffixTests : FullLineCompletionTestCase() {
  override fun getBasePath() = "testData/completion/features/suffix"

  fun `test suffix with override chars`() = doEnterTest("Python", "map(int, input().split(\"")

  fun `test suffix with override suffix`() = doEnterTest("Python", "map(int, input().split")

  // https://jetbrains.slack.com/archives/GKW1WVAV7/p1644331514433269
  fun `test in string char suffix`() = doEnterTest("Python", "{color}.txt\", \"r\") as f:")

  // https://jetbrains.slack.com/archives/GKW1WVAV7/p1644331584258539
  fun `test suffix after multiple strings`() = doEnterTest("Python", "t\", \"--with_target\", action=\"store_true")
}
