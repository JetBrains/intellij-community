package org.jetbrains.plugins.feature.suggester.suggesters

class SafeDeleteSuggesterTest : FeatureSuggesterTest() {

    fun `testRemove variable declaration and get suggestion`() {
        testSuggestionFound({
            myFixture.configureByFile("ClassMethodFieldCodeExample.java")
            removeSymbols(12, 0)
        }, {
            it.message == SafeDeleteSuggester.POPUP_MESSAGE
        })
    }

    fun `testRemove field declaration and get suggestion`() {
        testSuggestionFound({
            myFixture.configureByFile("ClassMethodFieldCodeExample.java")
            moveCaretRelatively(-4, -5, false)
            removeSymbols(42, 0)
        }, {
            it.message == SafeDeleteSuggester.POPUP_MESSAGE
        })
    }

    fun `testRemove method declaration and get suggestion`() {
        testSuggestionFound({
            myFixture.configureByFile("ClassMethodFieldCodeExample.java")
            moveCaretRelatively(-4, 8, false)
            removeSymbols(1, 2)
        }, {
            it.message == SafeDeleteSuggester.POPUP_MESSAGE
        })
    }

    fun `testRemove class declaration and get suggestion`() {
        testSuggestionFound({
            myFixture.configureByFile("ClassMethodFieldCodeExample.java")
            moveCaretRelatively(-4, 12, false)
            removeSymbols(1, 4)
        }, {
            it.message == SafeDeleteSuggester.POPUP_MESSAGE
        })
    }
}