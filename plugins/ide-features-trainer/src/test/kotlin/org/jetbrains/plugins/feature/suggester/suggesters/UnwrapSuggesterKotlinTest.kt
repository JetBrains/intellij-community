package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.openapi.application.invokeLater
import junit.framework.TestCase
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.suggesters.UnwrapSuggester.Companion.POPUP_MESSAGE
import org.jetbrains.plugins.feature.suggester.suggesters.UnwrapSuggester.Companion.SUGGESTING_ACTION_ID

class UnwrapSuggesterKotlinTest : UnwrapSuggesterTest() {

    override val testingCodeFileName = "KotlinCodeExample.kt"

    override fun `testUnwrap IF statement and get suggestion`() {
        selectBetweenLogicalPositions(lineStartIndex = 9, columnStartIndex = 42, lineEndIndex = 9, columnEndIndex = 3)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(11, 9)
        deleteSymbolAtCaret()

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testUnwrap one-line IF and get suggestion`() {
        selectBetweenLogicalPositions(lineStartIndex = 31, columnStartIndex = 23, lineEndIndex = 31, columnEndIndex = 8)
        deleteSymbolAtCaret()
        moveCaretRelatively(6, 0)
        deleteSymbolAtCaret()

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testUnwrap IF with deleting multiline selection and get suggestion`() {
        selectBetweenLogicalPositions(lineStartIndex = 8, columnStartIndex = 23, lineEndIndex = 10, columnEndIndex = 5)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(9, 9)
        deleteSymbolAtCaret()

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testUnwrap FOR and get suggestion`() {
        selectBetweenLogicalPositions(lineStartIndex = 22, columnStartIndex = 34, lineEndIndex = 22, columnEndIndex = 9)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(25, 13)
        deleteSymbolAtCaret()

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testUnwrap WHILE and get suggestion`() {
        selectBetweenLogicalPositions(lineStartIndex = 27, columnStartIndex = 27, lineEndIndex = 27, columnEndIndex = 0)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(30, 13)
        deleteSymbolAtCaret()

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testUnwrap commented IF and don't get suggestion`() {
        insertNewLineAt(21, 12)
        type(
            """//if(true) {
            |//i++; j--;
            |//}""".trimMargin()
        )

        selectBetweenLogicalPositions(
            lineStartIndex = 21,
            columnStartIndex = 14,
            lineEndIndex = 21,
            columnEndIndex = 24
        )
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(23, 15)
        deleteSymbolAtCaret()

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    override fun `testUnwrap IF written in string block and don't get suggestion`() {
        insertNewLineAt(21, 12)
        type("val s = \"\"\"if(true) {\ni++\nj--\n}")

        selectBetweenLogicalPositions(
            lineStartIndex = 21,
            columnStartIndex = 23,
            lineEndIndex = 21,
            columnEndIndex = 33
        )
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(24, 18)
        deleteSymbolAtCaret()

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }
}