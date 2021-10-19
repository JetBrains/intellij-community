package training.featuresSuggester.unwrap

import junit.framework.TestCase
import training.featuresSuggester.NoSuggestion

class UnwrapSuggesterJSTest : UnwrapSuggesterTest() {

    override val testingCodeFileName: String = "JavaScriptCodeExample.js"

    override fun `testUnwrap IF statement and get suggestion`() {
        moveCaretToLogicalPosition(22, 9)
        deleteSymbolAtCaret()
        selectBetweenLogicalPositions(lineStartIndex = 20, columnStartIndex = 49, lineEndIndex = 20, columnEndIndex = 3)
        deleteSymbolAtCaret()

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testUnwrap one-line IF and get suggestion`() {
        selectBetweenLogicalPositions(
            lineStartIndex = 56,
            columnStartIndex = 18,
            lineEndIndex = 56,
            columnEndIndex = 8
        )
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(56, 14)
        deleteSymbolAtCaret()

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testUnwrap IF with deleting multiline selection and get suggestion`() {
        selectBetweenLogicalPositions(lineStartIndex = 19, columnStartIndex = 22, lineEndIndex = 21, columnEndIndex = 5)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(20, 9)
        deleteSymbolAtCaret()

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testUnwrap FOR and get suggestion`() {
        selectBetweenLogicalPositions(lineStartIndex = 47, columnStartIndex = 36, lineEndIndex = 47, columnEndIndex = 8)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(50, 9)
        deleteSymbolAtCaret()

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testUnwrap WHILE and get suggestion`() {
        selectBetweenLogicalPositions(lineStartIndex = 52, columnStartIndex = 22, lineEndIndex = 52, columnEndIndex = 0)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(55, 9)
        deleteSymbolAtCaret()

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testUnwrap commented IF and don't get suggestion`() {
        insertNewLineAt(46, 8)
        type(
            """//if(true) {
            |//i++; j--;
            |//}""".trimMargin()
        )

        selectBetweenLogicalPositions(
            lineStartIndex = 46,
            columnStartIndex = 10,
            lineEndIndex = 46,
            columnEndIndex = 20
        )
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(48, 11)
        deleteSymbolAtCaret()

        testInvokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    override fun `testUnwrap IF written in string block and don't get suggestion`() {
        insertNewLineAt(46, 8)
        type(
            """let string = "if(true) {
            |i++; j--;
            |}"""".trimMargin()
        )

        selectBetweenLogicalPositions(
            lineStartIndex = 46,
            columnStartIndex = 22,
            lineEndIndex = 46,
            columnEndIndex = 32
        )
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(48, 14)
        deleteSymbolAtCaret()

        testInvokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }
}
