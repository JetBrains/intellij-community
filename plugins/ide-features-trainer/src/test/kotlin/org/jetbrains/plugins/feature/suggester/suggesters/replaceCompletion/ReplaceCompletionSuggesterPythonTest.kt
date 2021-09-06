package org.jetbrains.plugins.feature.suggester.suggesters.replaceCompletion

import com.intellij.openapi.application.invokeLater
import org.jetbrains.plugins.feature.suggester.NoSuggestion

class ReplaceCompletionSuggesterPythonTest : ReplaceCompletionSuggesterTest() {

    override val testingCodeFileName: String = "PythonCodeExample.py"

    override fun `testDelete and type dot, complete method call, remove previous identifier and get suggestion`() {
        moveCaretToLogicalPosition(27, 13)
        deleteAndTypeDot()
        val variants = completeBasic() ?: fail()
        chooseCompletionItem(variants[0])
        repeat(8) { typeDelete() }

        invokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testCall completion, complete method call, remove previous identifier and get suggestion`() {
        moveCaretToLogicalPosition(52, 47)
        val variants = completeBasic() ?: fail()
        chooseCompletionItem(variants[1])
        deleteTextBetweenLogicalPositions(
            lineStartIndex = 52,
            columnStartIndex = 52,
            lineEndIndex = 52,
            columnEndIndex = 76
        )

        invokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testCall completion, complete with method call, add parameter to method call, remove previous identifier and get suggestion`() {
        moveCaretToLogicalPosition(52, 47)
        val variants = completeBasic() ?: fail()
        chooseCompletionItem(variants[1])
        type("123")
        deleteTextBetweenLogicalPositions(
            lineStartIndex = 52,
            columnStartIndex = 56,
            lineEndIndex = 52,
            columnEndIndex = 79
        )

        invokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testCall completion, complete with property, remove previous identifier and get suggestion`() {
        moveCaretToLogicalPosition(52, 18)
        val variants = completeBasic() ?: fail()
        chooseCompletionItem(variants[0])
        repeat(23) { typeDelete() }

        invokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testCall completion inside arguments list, complete method call, remove previous identifier and get suggestion`() {
        moveCaretToLogicalPosition(52, 80)
        val variants = completeBasic() ?: fail()
        chooseCompletionItem(variants[1])
        repeat(5) { typeDelete() }

        invokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testCall completion, type additional characters, complete, remove previous identifier and get suggestion`() {
        moveCaretToLogicalPosition(52, 18)
        completeBasic()
        type("fu")
        val variants = getLookupElements() ?: fail()
        chooseCompletionItem(variants[0])
        repeat(25) { typeDelete() }

        invokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testCall completion, complete method call, remove another equal identifier and don't get suggestion`() {
        moveCaretToLogicalPosition(52, 47)
        val variants = completeBasic() ?: fail()
        chooseCompletionItem(variants[1])
        deleteTextBetweenLogicalPositions(
            lineStartIndex = 53,
            columnStartIndex = 8,
            lineEndIndex = 53,
            columnEndIndex = 35
        )

        invokeLater {
            assertTrue(expectedSuggestion is NoSuggestion)
        }
    }
}
