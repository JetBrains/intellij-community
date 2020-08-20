package org.jetbrains.plugins.feature.suggester.suggesters.replaceCompletion

import com.intellij.openapi.application.invokeLater
import junit.framework.TestCase
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.suggesters.ReplaceCompletionSuggester.Companion.POPUP_MESSAGE
import org.jetbrains.plugins.feature.suggester.suggesters.ReplaceCompletionSuggester.Companion.SUGGESTING_ACTION_ID

class ReplaceCompletionSuggesterJavaTest : ReplaceCompletionSuggesterTest() {

    override val testingCodeFileName: String = "JavaCodeExample.java"

    override fun `testDelete and type dot, complete method call, remove previous identifier and get suggestion`() {
        moveCaretToLogicalPosition(12, 20)
        deleteAndTypeDot()
        val variants = completeBasic() ?: fail()
        chooseCompletionItem(variants[0])
        repeat(5) { typeDelete() }

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testCall completion, complete method call, remove previous identifier and get suggestion`() {
        moveCaretToLogicalPosition(61, 60)
        val variants = completeBasic() ?: fail()
        chooseCompletionItem(variants[2])
        deleteTextBetweenLogicalPositions(
            lineStartIndex = 61,
            columnStartIndex = 75,
            lineEndIndex = 61,
            columnEndIndex = 97
        )

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testCall completion, complete with method call, add parameter to method call, remove previous identifier and get suggestion`() {
        moveCaretToLogicalPosition(61, 60)
        val variants = completeBasic() ?: fail()
        chooseCompletionItem(variants[2])
        type("123")
        deleteTextBetweenLogicalPositions(
            lineStartIndex = 61,
            columnStartIndex = 79,
            lineEndIndex = 61,
            columnEndIndex = 100
        )

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testCall completion, complete with property, remove previous identifier and get suggestion`() {
        moveCaretToLogicalPosition(61, 33)
        val variants = completeBasic() ?: fail()
        chooseCompletionItem(variants[0])
        repeat(21) { typeDelete() }

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testCall completion inside arguments list, complete method call, remove previous identifier and get suggestion`() {
        moveCaretToLogicalPosition(61, 91)
        val variants = completeBasic() ?: fail()
        chooseCompletionItem(variants[1])
        repeat(15) { typeDelete() }

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testCall completion, type additional characters, complete, remove previous identifier and get suggestion`() {
        moveCaretToLogicalPosition(61, 33)
        completeBasic()
        type("cycles")
        val variants = getLookupElements() ?: fail()
        chooseCompletionItem(variants[0])
        repeat(22) { typeDelete() }

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testCall completion, complete method call, remove another equal identifier and don't get suggestion`() {
        moveCaretToLogicalPosition(61, 60)
        val variants = completeBasic() ?: fail()
        chooseCompletionItem(variants[1])
        deleteTextBetweenLogicalPositions(
            lineStartIndex = 62,
            columnStartIndex = 20,
            lineEndIndex = 62,
            columnEndIndex = 45
        )

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }
}