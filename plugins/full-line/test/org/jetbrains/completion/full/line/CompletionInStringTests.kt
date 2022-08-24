package org.jetbrains.completion.full.line

import org.jetbrains.completion.full.line.platform.tests.FullLineCompletionTestCase
import org.junit.jupiter.api.assertAll

class CompletionInStringTests : FullLineCompletionTestCase() {
  override fun getBasePath() = "testData/completion/strings"

  fun `test DocStringCursorPosition`() = assertAll(
    { doTabTest("Python", "rtype: function") },
    { doEnterTest("Python", "rtype: function") }
  )

  fun `test DocStringShift`() = doEnterTest("Python", "object pointer")

  fun `test EnterBehaviour`() = doEnterTest("Python", "<filename>\")")

  fun `test RedundantQuote`() = doEnterTest("Python", "abstract method\")")

  fun `test StringTab`() = doTabTest("Python", "Enter a string: \")")

  fun `test simple`() = assertAll(
    {
      doTabTest(
        "Python",
        "os.path.dirname(__file__)",
        "os.path.dirname(os.path.abspath(__file__))",
        "os.getcwd()"
      )
    },
    {
      doEnterTest(
        "Python",
        "os.path.dirname(__file__)",
        "os.path.dirname(os.path.abspath(__file__))",
        "os.getcwd()"
      )
    },
  )
}
