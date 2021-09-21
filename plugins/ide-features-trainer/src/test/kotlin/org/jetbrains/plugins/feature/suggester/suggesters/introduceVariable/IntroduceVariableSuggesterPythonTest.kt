package org.jetbrains.plugins.feature.suggester.suggesters.introduceVariable

/**
 * Note: when user is declaring variable and it's name starts with any language keyword suggestion will not be thrown
 */
class IntroduceVariableSuggesterPythonTest : IntroduceVariableSuggesterTest() {

    override val testingCodeFileName: String = "PythonCodeExample.py"

    override fun `testIntroduce expression from IF and get suggestion`() {
        cutBetweenLogicalPositions(lineStartIndex = 3, columnStartIndex = 2, lineEndIndex = 3, columnEndIndex = 12)
        insertNewLineAt(3)
        type("eee =")
        pasteFromClipboard()
        moveCaretToLogicalPosition(4, 2)
        type(" eee")

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testIntroduce full expression from method call and get suggestion`() {
        cutBetweenLogicalPositions(lineStartIndex = 27, columnStartIndex = 25, lineEndIndex = 27, columnEndIndex = 57)
        insertNewLineAt(27, 8)
        type("value = ")
        pasteFromClipboard()
        moveCaretToLogicalPosition(28, 25)
        type("value")

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testIntroduce part of expression from method call and get suggestion`() {
        cutBetweenLogicalPositions(lineStartIndex = 27, columnStartIndex = 56, lineEndIndex = 27, columnEndIndex = 46)
        insertNewLineAt(27, 8)
        type("val = ")
        pasteFromClipboard()
        moveCaretToLogicalPosition(28, 46)
        type("val")

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testIntroduce part of string expression from method call and get suggestion`() {
        cutBetweenLogicalPositions(lineStartIndex = 36, columnStartIndex = 18, lineEndIndex = 36, columnEndIndex = 37)
        insertNewLineAt(36, 4)
        type("str = ")
        pasteFromClipboard()
        moveCaretToLogicalPosition(37, 18)
        type("str")

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testIntroduce full expression from return statement and get suggestion`() {
        cutBetweenLogicalPositions(lineStartIndex = 37, columnStartIndex = 11, lineEndIndex = 37, columnEndIndex = 42)
        insertNewLineAt(37, 4)
        type("output = ")
        pasteFromClipboard()
        moveCaretToLogicalPosition(38, 11)
        type("output")

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testIntroduce expression from method body using copy and backspace and get suggestion`() {
        selectBetweenLogicalPositions(
            lineStartIndex = 27,
            columnStartIndex = 42,
            lineEndIndex = 27,
            columnEndIndex = 56
        )
        copyCurrentSelection()
        selectBetweenLogicalPositions(
            lineStartIndex = 27,
            columnStartIndex = 42,
            lineEndIndex = 27,
            columnEndIndex = 56
        )
        deleteSymbolAtCaret()
        insertNewLineAt(27, 8)
        type("out=")
        pasteFromClipboard()
        moveCaretToLogicalPosition(28, 42)
        type("out")

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    fun `testIntroduce part of string declaration expression and get suggestion`() {
        cutBetweenLogicalPositions(lineStartIndex = 35, columnStartIndex = 13, lineEndIndex = 35, columnEndIndex = 24)
        insertNewLineAt(35, 4)
        type("str = ")
        pasteFromClipboard()
        type(";")
        moveCaretToLogicalPosition(36, 13)
        type("str")

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }
}
