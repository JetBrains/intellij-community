package training.featuresSuggester.introduceVariable

import junit.framework.TestCase
import training.featuresSuggester.NoSuggestion

class IntroduceVariableSuggesterJSTest : IntroduceVariableSuggesterTest() {

  override val testingCodeFileName: String = "JavaScriptCodeExample.js"

  override fun `testIntroduce expression from IF and get suggestion`() {
    val doc = editor.document
    cutBetweenLogicalPositions(lineStartIndex = 20, columnStartIndex = 23, lineEndIndex = 20, columnEndIndex = 46)
    insertNewLineAt(20, 8)
    type("let flag =")
    pasteFromClipboard()
    moveCaretToLogicalPosition(21, 23)
    type(" flag")

    testInvokeLater {
      println(doc.text)
      assertSuggestedCorrectly()
    }
  }

  override fun `testIntroduce full expression from method call and get suggestion`() {
    cutBetweenLogicalPositions(lineStartIndex = 21, columnStartIndex = 24, lineEndIndex = 21, columnEndIndex = 63)
    insertNewLineAt(21, 12)
    type("var value = ")
    pasteFromClipboard()
    moveCaretToLogicalPosition(22, 24)
    type("value")

    testInvokeLater {
      assertSuggestedCorrectly()
    }
  }

  override fun `testIntroduce part of expression from method call and get suggestion`() {
    cutBetweenLogicalPositions(lineStartIndex = 21, columnStartIndex = 33, lineEndIndex = 21, columnEndIndex = 24)
    insertNewLineAt(21, 12)
    type("let val = ")
    pasteFromClipboard()
    moveCaretToLogicalPosition(22, 24)
    type("val")

    testInvokeLater {
      assertSuggestedCorrectly()
    }
  }

  override fun `testIntroduce part of string expression from method call and get suggestion`() {
    cutBetweenLogicalPositions(lineStartIndex = 62, columnStartIndex = 30, lineEndIndex = 62, columnEndIndex = 15)
    insertNewLineAt(62, 8)
    type("const tring = ")
    pasteFromClipboard()
    moveCaretToLogicalPosition(63, 15)
    type("tring")

    testInvokeLater {
      assertSuggestedCorrectly()
    }
  }

  override fun `testIntroduce full expression from return statement and get suggestion`() {
    cutBetweenLogicalPositions(lineStartIndex = 63, columnStartIndex = 15, lineEndIndex = 63, columnEndIndex = 51)
    insertNewLineAt(63, 8)
    type("let bool= ")
    pasteFromClipboard()
    moveCaretToLogicalPosition(64, 15)
    type("bool")

    testInvokeLater {
      assertSuggestedCorrectly()
    }
  }

  override fun `testIntroduce expression from method body using copy and backspace and get suggestion`() {
    selectBetweenLogicalPositions(
      lineStartIndex = 37,
      columnStartIndex = 30,
      lineEndIndex = 37,
      columnEndIndex = 40
    )
    copyCurrentSelection()
    selectBetweenLogicalPositions(
      lineStartIndex = 37,
      columnStartIndex = 30,
      lineEndIndex = 37,
      columnEndIndex = 40
    )
    deleteSymbolAtCaret()
    insertNewLineAt(37, 8)
    type("let output = ")
    pasteFromClipboard()
    moveCaretToLogicalPosition(38, 30)
    type("output")

    testInvokeLater {
      assertSuggestedCorrectly()
    }
  }

  /**
   * This case must throw suggestion but not working now
   */
  fun `testIntroduce part of string declaration expression and don't get suggestion`() {
    cutBetweenLogicalPositions(lineStartIndex = 61, columnStartIndex = 24, lineEndIndex = 61, columnEndIndex = 37)
    insertNewLineAt(61, 8)
    type("let trrr = ")
    pasteFromClipboard()
    moveCaretToLogicalPosition(62, 24)
    type("trrr")

    testInvokeLater {
      TestCase.assertTrue(expectedSuggestion is NoSuggestion)
    }
  }
}
