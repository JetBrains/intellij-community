package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.openapi.application.invokeLater
import junit.framework.TestCase
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.PopupSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion

class UnwrapSuggesterTest : FeatureSuggesterTest() {

    override val testingCodeFileName = "JavaCodeExample.java"

    fun `testUnwrap IF statement and get suggestion`() {
        selectBetweenLogicalPositions(lineStartIndex = 9, columnStartIndex = 41, lineEndIndex = 9, columnEndIndex = 3)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(11, 9)
        deleteSymbolAtCaret()

        invokeLater {
            assertSuggestedCorrectly(expectedSuggestion)
        }
    }

    fun `testUnwrap one-line IF and get suggestion`() {
        selectBetweenLogicalPositions(lineStartIndex = 41, columnStartIndex = 25, lineEndIndex = 41, columnEndIndex = 12)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(41, 18)
        deleteSymbolAtCaret()

        invokeLater {
            assertSuggestedCorrectly(expectedSuggestion)
        }
    }

    fun `testUnwrap IF with deleting multiline selection and get suggestion`() {
        selectBetweenLogicalPositions(lineStartIndex = 8, columnStartIndex = 23, lineEndIndex = 10, columnEndIndex = 5)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(9, 9)
        deleteSymbolAtCaret()

        invokeLater {
            assertSuggestedCorrectly(expectedSuggestion)
        }
    }

    fun `testUnwrap FOR and get suggestion`() {
        selectBetweenLogicalPositions(lineStartIndex = 32, columnStartIndex = 40, lineEndIndex = 32, columnEndIndex = 9)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(35, 13)
        deleteSymbolAtCaret()

        invokeLater {
            assertSuggestedCorrectly(expectedSuggestion)
        }
    }

    fun `testUnwrap WHILE and get suggestion`() {
        selectBetweenLogicalPositions(lineStartIndex = 37, columnStartIndex = 26, lineEndIndex = 37, columnEndIndex = 0)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(40, 13)
        deleteSymbolAtCaret()

        invokeLater {
            assertSuggestedCorrectly(expectedSuggestion)
        }
    }

    fun `testUnwrap commented IF and don't get suggestion`() {
        insertNewLineAt(31, 12)
        type("""//if(true) {
            |//i++; j--;
            |//}""".trimMargin()
        )

        selectBetweenLogicalPositions(lineStartIndex = 31, columnStartIndex = 14, lineEndIndex = 31, columnEndIndex = 24)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(33, 15)
        deleteSymbolAtCaret()

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    fun `testUnwrap IF written in string block and don't get suggestion`() {
        insertNewLineAt(31, 12)
        type("""val s = "if(true) {
            |i++; j--;
            |}"""".trimMargin()
        )

        selectBetweenLogicalPositions(lineStartIndex = 31, columnStartIndex = 21, lineEndIndex = 31, columnEndIndex = 31)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(33, 22)
        deleteSymbolAtCaret()

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    private fun assertSuggestedCorrectly(suggestion: Suggestion) {
        TestCase.assertTrue(suggestion is PopupSuggestion)
        TestCase.assertEquals(UnwrapSuggester.POPUP_MESSAGE, (suggestion as PopupSuggestion).message)
    }
}