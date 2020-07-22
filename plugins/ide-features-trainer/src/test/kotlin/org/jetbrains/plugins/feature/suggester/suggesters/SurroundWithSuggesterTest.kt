package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.openapi.application.invokeLater
import junit.framework.TestCase
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.suggesters.SurroundWithSuggester.Companion.POPUP_MESSAGE
import org.jetbrains.plugins.feature.suggester.suggesters.SurroundWithSuggester.Companion.SUGGESTING_ACTION_ID

class SurroundWithSuggesterTest : FeatureSuggesterTest() {

    override val testingCodeFileName: String = "JavaCodeExample.java"

    fun `testSurround one statement with IF and get suggestion`() {
        insertNewLineAt(6)
        type("if () {")
        insertNewLineAt(8)
        type("}")

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    fun `testSurround 2 statements with IF and add '}' at the line with second statement and get suggestion`() {
        insertNewLineAt(5)
        type("if (true){")
        moveCaretToLogicalPosition(7, 20)
        type("}")

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    fun `testSurround all statements in block with IF and get suggestion`() {
        insertNewLineAt(5)
        type("if(){")
        insertNewLineAt(14)
        type("}")

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    fun `testSurround one statement with IF in one line and get suggestion`() {
        moveCaretToLogicalPosition(6, 8)
        type("if(1 > 2 ){")
        moveCaretRelatively(12, 0)
        type("}")

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    fun `testSurround statements with FOR and get suggestion`() {
        insertNewLineAt(6)
        type("for (int i = 0; i < 10; i++) {")
        insertNewLineAt(13)
        type("}")

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    fun `testSurround statements with WHILE and get suggestion`() {
        insertNewLineAt(7)
        type("while(false && true){")
        insertNewLineAt(10)
        type("}")

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    /**
     * This case must throw suggestion but not working now
     */
    fun `testSurround IfStatement with IF and don't get suggestion`() {
        insertNewLineAt(9)
        type("if (false && true){")
        insertNewLineAt(13)
        type("}")

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    /**
     * This case must throw suggestion but not working now
     */
    fun `testSurround statements with FOREACH and don't get suggestion`() {
        insertNewLineAt(6)
        type("for (int a: list) {")
        insertNewLineAt(13)
        type("}")

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    fun `testSurround 0 statements with IF and don't get suggestion`() {
        insertNewLineAt(6)
        type("if (true) {    }")

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }
}