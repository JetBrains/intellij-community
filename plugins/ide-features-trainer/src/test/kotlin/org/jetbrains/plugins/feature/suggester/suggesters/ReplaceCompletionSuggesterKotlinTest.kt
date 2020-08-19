package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.openapi.application.invokeLater
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.suggesters.ReplaceCompletionSuggester.Companion.POPUP_MESSAGE
import org.jetbrains.plugins.feature.suggester.suggesters.ReplaceCompletionSuggester.Companion.SUGGESTING_ACTION_ID

class ReplaceCompletionSuggesterKotlinTest : ReplaceCompletionSuggesterTest() {

    override val testingCodeFileName: String = "KotlinCodeExample.kt"

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
        moveCaretToLogicalPosition(64, 57)
        val variants = completeBasic() ?: fail()
        chooseCompletionItem(variants[3])
        deleteTextBetweenLogicalPositions(
            lineStartIndex = 64,
            columnStartIndex = 72,
            lineEndIndex = 64,
            columnEndIndex = 94
        )

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testCall completion, complete with method call, add parameter to method call, remove previous identifier and get suggestion`() {
        moveCaretToLogicalPosition(64, 57)
        val variants = completeBasic() ?: fail()
        chooseCompletionItem(variants[3])
        type("132")
        deleteTextBetweenLogicalPositions(
            lineStartIndex = 64,
            columnStartIndex = 76,
            lineEndIndex = 64,
            columnEndIndex = 97
        )

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testCall completion, complete with property, remove previous identifier and get suggestion`() {
        moveCaretToLogicalPosition(64, 30)
        val variants = completeBasic() ?: fail()
        chooseCompletionItem(variants[0])
        repeat(21) { typeDelete() }

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testCall completion inside arguments list, complete method call, remove previous identifier and get suggestion`() {
        moveCaretToLogicalPosition(64, 88)
        val variants = completeBasic() ?: fail()
        chooseCompletionItem(variants[0])
        repeat(15) { typeDelete() }

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testCall completion, type additional characters, complete, remove previous identifier and get suggestion`() {
        moveCaretToLogicalPosition(64, 30)
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
        moveCaretToLogicalPosition(64, 57)
        val variants = completeBasic() ?: fail()
        chooseCompletionItem(variants[3])
        deleteTextBetweenLogicalPositions(
            lineStartIndex = 65,
            columnStartIndex = 16,
            lineEndIndex = 65,
            columnEndIndex = 41
        )

        invokeLater {
            assertTrue(expectedSuggestion is NoSuggestion)
        }
    }
}