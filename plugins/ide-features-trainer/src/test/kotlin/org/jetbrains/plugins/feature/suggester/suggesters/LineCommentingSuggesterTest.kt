package org.jetbrains.plugins.feature.suggester.suggesters

class LineCommentingSuggesterTest : FeatureSuggesterTest() {

    fun `testComment one line and get suggestion`() {
        testFeatureFound({
            myFixture.apply {
                configureByFile("SimpleCodeExample.java")
                type("//")
            }
        }, {
            it.message == LineCommentingSuggester.POPUP_MESSAGE
        })
    }
}