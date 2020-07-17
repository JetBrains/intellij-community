package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.openapi.application.invokeLater
import junit.framework.TestCase
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.PopupSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion

class LineCommentingSuggesterJavaTest : FeatureSuggesterTest() {

    override val testingCodeFileName: String = "JavaCodeExample.java"

    fun `testComment 3 lines in a row and get suggestion`() {
        moveCaretToLogicalPosition(6, 8)
        type("//")
        moveCaretToLogicalPosition(7, 8)
        type("//")
        moveCaretToLogicalPosition(8, 8)
        type("//")

        invokeLater {
            assertSuggestedCorrectly(expectedSuggestion)
        }
    }

    fun `testComment 3 lines in different order and get suggestion`() {
        moveCaretToLogicalPosition(9, 5)
        type("//")
        moveCaretToLogicalPosition(11, 0)
        type("//")
        moveCaretToLogicalPosition(10, 8)
        type("//")

        invokeLater {
            assertSuggestedCorrectly(expectedSuggestion)
        }
    }

    fun `testComment two lines and one empty line and don't get suggestion`() {
        moveCaretToLogicalPosition(12, 3)
        type("//")
        moveCaretToLogicalPosition(13, 1)
        type("//")
        moveCaretToLogicalPosition(14, 0)
        type("//")

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    fun `testComment two lines in a row and one with interval and don't get suggestion`() {
        moveCaretToLogicalPosition(32, 0)
        type("//")
        moveCaretToLogicalPosition(33, 0)
        type("//")
        moveCaretToLogicalPosition(35, 0)
        type("//")

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    fun `testComment 3 already commented lines and don't get suggestion`() {
        insertNewLineAt(42, 12)
        type(
            """//if(true) {
            |//i++; j--;
            |//}""".trimMargin()
        )

        moveCaretToLogicalPosition(42, 2)
        type("//")
        moveCaretToLogicalPosition(43, 2)
        type("//")
        moveCaretToLogicalPosition(44, 2)
        type("//")

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    fun `testComment 3 lines of block comment and don't get suggestion`() {
        insertNewLineAt(42, 12)
        type(
            """/*
            |if(true) {
            |    i++; j--;
            |}""".trimMargin()
        )

        moveCaretToLogicalPosition(43, 4)
        type("//")
        moveCaretToLogicalPosition(44, 4)
        type("//")
        moveCaretToLogicalPosition(45, 4)
        type("//")

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    private fun assertSuggestedCorrectly(suggestion: Suggestion) {
        TestCase.assertTrue(suggestion is PopupSuggestion)
        TestCase.assertEquals(LineCommentingSuggester.POPUP_MESSAGE, (suggestion as PopupSuggestion).message)
    }
}