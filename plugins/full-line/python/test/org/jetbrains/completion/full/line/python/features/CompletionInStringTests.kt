package org.jetbrains.completion.full.line.python.features

import org.jetbrains.completion.full.line.python.tests.FullLinePythonCompletionTestCase
import org.junit.jupiter.api.Assertions.assertAll

class CompletionInStringTests : FullLinePythonCompletionTestCase() {
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
