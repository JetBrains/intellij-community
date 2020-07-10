package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.openapi.application.invokeLater
import junit.framework.TestCase
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.PopupSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion

class CopyPasteSuggesterTest : FeatureSuggesterTest() {

    override val testingCodeFileName: String = "PythonCodeExample.py"

    fun `testCopy text that contained in clipboard at first index and get suggestion`() {
        copyBetweenLogicalPositions(lineStartIndex = 6, columnStartIndex = 14, lineEndIndex = 6, columnEndIndex = 0)
        copyBetweenLogicalPositions(lineStartIndex = 5, columnStartIndex = 5, lineEndIndex = 5, columnEndIndex = 0)
        copyBetweenLogicalPositions(lineStartIndex = 6, columnStartIndex = 14, lineEndIndex = 6, columnEndIndex = 0)

        invokeLater {
            assertSuggestedCorrectly(expectedSuggestion)
        }
    }

    fun `testCopy text that contained in clipboard at second index and get suggestion`() {
        copyBetweenLogicalPositions(lineStartIndex = 9, columnStartIndex = 23, lineEndIndex = 9, columnEndIndex = 0)
        copyBetweenLogicalPositions(lineStartIndex = 10, columnStartIndex = 23, lineEndIndex = 10, columnEndIndex = 0)
        copyBetweenLogicalPositions(lineStartIndex = 11, columnStartIndex = 18, lineEndIndex = 11, columnEndIndex = 0)
        copyBetweenLogicalPositions(lineStartIndex = 9, columnStartIndex = 23, lineEndIndex = 9, columnEndIndex = 0)

        invokeLater {
            assertSuggestedCorrectly(expectedSuggestion)
        }
    }

    fun `testCopy same text twice in a row and don't get suggestion`() {
        copyBetweenLogicalPositions(lineStartIndex = 31, columnStartIndex = 11, lineEndIndex = 31, columnEndIndex = 16)
        copyBetweenLogicalPositions(lineStartIndex = 17, columnStartIndex = 6, lineEndIndex = 17, columnEndIndex = 11)

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    fun `testCopy text that contained in clipboard at third index and dont't get suggestion`() {

        copyBetweenLogicalPositions(lineStartIndex = 26, columnStartIndex = 31, lineEndIndex = 26, columnEndIndex = 0)
        copyBetweenLogicalPositions(lineStartIndex = 27, columnStartIndex = 58, lineEndIndex = 27, columnEndIndex = 0)
        copyBetweenLogicalPositions(lineStartIndex = 28, columnStartIndex = 25, lineEndIndex = 28, columnEndIndex = 0)
        copyBetweenLogicalPositions(lineStartIndex = 32, columnStartIndex = 23, lineEndIndex = 32, columnEndIndex = 0)
        copyBetweenLogicalPositions(lineStartIndex = 6, columnStartIndex = 14, lineEndIndex = 6, columnEndIndex = 0)

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    private fun assertSuggestedCorrectly(suggestion: Suggestion) {
        TestCase.assertTrue(suggestion is PopupSuggestion)
        TestCase.assertEquals(CopyPasteSuggester.POPUP_MESSAGE, (suggestion as PopupSuggestion).message)
    }
}