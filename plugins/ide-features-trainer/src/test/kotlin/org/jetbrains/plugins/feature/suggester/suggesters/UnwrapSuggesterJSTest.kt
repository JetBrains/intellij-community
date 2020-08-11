package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.openapi.application.invokeLater
import junit.framework.TestCase
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.suggesters.UnwrapSuggester.Companion.POPUP_MESSAGE
import org.jetbrains.plugins.feature.suggester.suggesters.UnwrapSuggester.Companion.SUGGESTING_ACTION_ID

class UnwrapSuggesterJSTest : UnwrapSuggesterTest() {

    override val testingCodeFileName: String = "JavaScriptCodeExample.js"

    override fun `testUnwrap IF statement and get suggestion`() {
        selectBetweenLogicalPositions(lineStartIndex = 20, columnStartIndex = 49, lineEndIndex = 20, columnEndIndex = 3)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(22, 9)
        deleteSymbolAtCaret()

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
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

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testUnwrap IF with deleting multiline selection and get suggestion`() {
        selectBetweenLogicalPositions(lineStartIndex = 19, columnStartIndex = 22, lineEndIndex = 21, columnEndIndex = 5)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(20, 9)
        deleteSymbolAtCaret()

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testUnwrap FOR and get suggestion`() {
        selectBetweenLogicalPositions(lineStartIndex = 47, columnStartIndex = 36, lineEndIndex = 47, columnEndIndex = 8)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(50, 9)
        deleteSymbolAtCaret()

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testUnwrap WHILE and get suggestion`() {
        selectBetweenLogicalPositions(lineStartIndex = 52, columnStartIndex = 22, lineEndIndex = 52, columnEndIndex = 0)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(55, 9)
        deleteSymbolAtCaret()

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
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

        invokeLater {
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

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }
}