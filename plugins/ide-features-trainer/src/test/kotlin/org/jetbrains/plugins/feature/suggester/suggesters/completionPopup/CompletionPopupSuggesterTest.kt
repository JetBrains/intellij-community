package org.jetbrains.plugins.feature.suggester.suggesters.completionPopup

import org.jetbrains.plugins.feature.suggester.suggesters.FeatureSuggesterTest

class CompletionPopupSuggesterTest : FeatureSuggesterTest() {

    override val testingCodeFileName: String = "JavaCodeExample.java"
    override val testingSuggesterId: String = "Completion"

    fun `testDelete and type dot, complete method call and get suggestion`() {
        moveCaretToLogicalPosition(12, 20)
        deleteSymbolAtCaret()
        type(".")
        val variants = completeBasic() ?: fail()
        chooseCompletionItem(variants[0])

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    fun `testDelete and type dot inside arguments list, complete with property and get suggestion`() {
        moveCaretToLogicalPosition(61, 91)
        deleteSymbolAtCaret()
        type(".")
        val variants = completeBasic() ?: fail()
        chooseCompletionItem(variants[0])

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    fun `testDelete and type dot, type additional characters, complete and get suggestion`() {
        moveCaretToLogicalPosition(61, 33)
        deleteSymbolAtCaret()
        type(".")
        completeBasic()
        type("cycles")
        val variants = getLookupElements() ?: fail()
        chooseCompletionItem(variants[0])

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }
}
