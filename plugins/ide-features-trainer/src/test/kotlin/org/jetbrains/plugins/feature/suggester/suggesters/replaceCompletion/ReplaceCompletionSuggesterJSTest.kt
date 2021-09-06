package org.jetbrains.plugins.feature.suggester.suggesters.replaceCompletion

import com.intellij.openapi.application.invokeLater
import org.jetbrains.plugins.feature.suggester.NoSuggestion

class ReplaceCompletionSuggesterJSTest : ReplaceCompletionSuggesterTest() {

    override val testingCodeFileName: String = "JavaScriptCodeExample.js"

    override fun `testDelete and type dot, complete method call, remove previous identifier and get suggestion`() {
        moveCaretToLogicalPosition(24, 21)
        deleteAndTypeDot()
        val variants = completeBasic() ?: fail()
        chooseCompletionItem(variants[1])
        repeat(5) { typeDelete() }

        invokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testCall completion, complete method call, remove previous identifier and get suggestion`() {
        moveCaretToLogicalPosition(72, 53)
        val variants = completeBasic() ?: fail()
        chooseCompletionItem(variants[0])
        deleteTextBetweenLogicalPositions(
            lineStartIndex = 72,
            columnStartIndex = 68,
            lineEndIndex = 72,
            columnEndIndex = 90
        )

        invokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testCall completion, complete with method call, add parameter to method call, remove previous identifier and get suggestion`() {
        moveCaretToLogicalPosition(72, 53)
        val variants = completeBasic() ?: fail()
        chooseCompletionItem(variants[0])
        type("123")
        deleteTextBetweenLogicalPositions(
            lineStartIndex = 72,
            columnStartIndex = 72,
            lineEndIndex = 72,
            columnEndIndex = 93
        )

        invokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testCall completion, complete with property, remove previous identifier and get suggestion`() {
        moveCaretToLogicalPosition(72, 26)
        val variants = completeBasic() ?: fail()
        chooseCompletionItem(variants[1])
        repeat(21) { typeDelete() }

        invokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testCall completion inside arguments list, complete method call, remove previous identifier and get suggestion`() {
        moveCaretToLogicalPosition(72, 84)
        val variants = completeBasic() ?: fail()
        chooseCompletionItem(variants[0])
        repeat(15) { typeDelete() }

        invokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testCall completion, type additional characters, complete, remove previous identifier and get suggestion`() {
        moveCaretToLogicalPosition(72, 26)
        completeBasic()
        type("cycles")
        val variants = getLookupElements() ?: fail()
        chooseCompletionItem(variants[0])
        repeat(22) { typeDelete() }

        invokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testCall completion, complete method call, remove another equal identifier and don't get suggestion`() {
        moveCaretToLogicalPosition(72, 53)
        val variants = completeBasic() ?: fail()
        chooseCompletionItem(variants[0])
        deleteTextBetweenLogicalPositions(
            lineStartIndex = 73,
            columnStartIndex = 12,
            lineEndIndex = 73,
            columnEndIndex = 37
        )

        invokeLater {
            assertTrue(expectedSuggestion is NoSuggestion)
        }
    }
}
