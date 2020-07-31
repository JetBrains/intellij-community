package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.openapi.application.invokeLater
import junit.framework.TestCase
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.suggesters.IntroduceVariableSuggester.Companion.POPUP_MESSAGE
import org.jetbrains.plugins.feature.suggester.suggesters.IntroduceVariableSuggester.Companion.SUGGESTING_ACTION_ID

/**
 * Note: when user is declaring variable and it's name starts with any language keyword suggestion will not be thrown
 */
class IntroduceVariableSuggesterKotlinTest : IntroduceVariableSuggesterTest() {

    override val testingCodeFileName: String = "KotlinCodeExample.kt"

    override fun `testIntroduce expression from IF and get suggestion`() {
        cutBetweenLogicalPositions(lineStartIndex = 9, columnStartIndex = 24, lineEndIndex = 9, columnEndIndex = 39)
        insertNewLineAt(9, 8)
        type("val flag =")
        pasteFromClipboard()
        moveCaretToLogicalPosition(10, 24)
        type(" flag")

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testIntroduce full expression from method call and get suggestion`() {
        cutBetweenLogicalPositions(lineStartIndex = 10, columnStartIndex = 37, lineEndIndex = 10, columnEndIndex = 20)
        insertNewLineAt(10, 12)
        type("var temp = ")
        pasteFromClipboard()
        moveCaretToLogicalPosition(11, 20)
        type("temp")

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testIntroduce part of expression from method call and get suggestion`() {
        cutBetweenLogicalPositions(lineStartIndex = 10, columnStartIndex = 29, lineEndIndex = 10, columnEndIndex = 20)
        insertNewLineAt(10, 12)
        type("val abcbcd = ")
        pasteFromClipboard()
        moveCaretToLogicalPosition(11, 20)
        type("abcbcd")

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testIntroduce part of string expression from method call and get suggestion`() {
        cutBetweenLogicalPositions(lineStartIndex = 37, columnStartIndex = 35, lineEndIndex = 37, columnEndIndex = 46)
        insertNewLineAt(37, 12)
        type("val serr = ")
        pasteFromClipboard()
        moveCaretToLogicalPosition(38, 35)
        type("serr")

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testIntroduce full expression from return statement and get suggestion`() {
        cutBetweenLogicalPositions(lineStartIndex = 50, columnStartIndex = 23, lineEndIndex = 50, columnEndIndex = 67)
        insertNewLineAt(50, 16)
        type("val bool=")
        pasteFromClipboard()
        moveCaretToLogicalPosition(51, 23)
        type("bool")

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testIntroduce expression from method body using copy and backspace and get suggestion`() {
        selectBetweenLogicalPositions(
            lineStartIndex = 28,
            columnStartIndex = 24,
            lineEndIndex = 28,
            columnEndIndex = 29
        )
        copyCurrentSelection()
        selectBetweenLogicalPositions(
            lineStartIndex = 28,
            columnStartIndex = 24,
            lineEndIndex = 28,
            columnEndIndex = 29
        )
        deleteSymbolAtCaret()
        insertNewLineAt(28, 16)
        type("var output =")
        pasteFromClipboard()
        moveCaretToLogicalPosition(29, 24)
        type("output")

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    /**
     * This case must throw suggestion but not working now
     */
    fun `testIntroduce part of string declaration expression and don't get suggestion`() {
        cutBetweenLogicalPositions(lineStartIndex = 40, columnStartIndex = 22, lineEndIndex = 40, columnEndIndex = 46)
        insertNewLineAt(40, 12)
        type("val string = ")
        pasteFromClipboard()
        moveCaretToLogicalPosition(41, 22)
        type("string")

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }
}