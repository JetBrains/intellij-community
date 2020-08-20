package org.jetbrains.plugins.feature.suggester.suggesters.lineCommenting

import com.intellij.openapi.application.invokeLater
import junit.framework.TestCase
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.suggesters.FeatureSuggesterTest
import org.jetbrains.plugins.feature.suggester.suggesters.LineCommentingSuggester.Companion.POPUP_MESSAGE
import org.jetbrains.plugins.feature.suggester.suggesters.LineCommentingSuggester.Companion.SUGGESTING_ACTION_ID

class LineCommentingSuggesterPythonTest : FeatureSuggesterTest() {

    override val testingCodeFileName = "PythonCodeExample.py"

    fun `testComment 3 lines in a row and get suggestion`() {
        moveCaretToLogicalPosition(3, 0)
        type("#")
        moveCaretToLogicalPosition(4, 0)
        type("#")
        moveCaretToLogicalPosition(5, 0)
        type("#")

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    fun `testComment 3 lines in different order and get suggestion`() {
        moveCaretToLogicalPosition(9, 0)
        type("#")
        moveCaretToLogicalPosition(11, 7)
        type("#")
        moveCaretToLogicalPosition(10, 2)
        type("#")

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    fun `testComment two lines and one empty line and don't get suggestion`() {
        moveCaretToLogicalPosition(17, 0)
        type("#")
        moveCaretToLogicalPosition(18, 0)
        type("#")
        moveCaretToLogicalPosition(19, 0)
        type("#")

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    fun `testComment two lines in a row and one with interval and don't get suggestion`() {
        moveCaretToLogicalPosition(21, 0)
        type("#")
        moveCaretToLogicalPosition(22, 0)
        type("#")
        moveCaretToLogicalPosition(24, 0)
        type("#")

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    fun `testComment 3 already commented lines and don't get suggestion`() {
        insertNewLineAt(25, 8)
        type(
            """        #if True:
            |#    i++
            |#    j--""".trimMargin()
        )

        moveCaretToLogicalPosition(25, 2)
        type("#")
        moveCaretToLogicalPosition(26, 2)
        type("#")
        moveCaretToLogicalPosition(27, 2)
        type("#")

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }
}