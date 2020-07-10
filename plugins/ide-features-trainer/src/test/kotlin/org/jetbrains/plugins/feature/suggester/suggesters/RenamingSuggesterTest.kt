package org.jetbrains.plugins.feature.suggester.suggesters

import junit.framework.TestCase
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.PopupSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion

abstract class RenamingSuggesterTest : FeatureSuggesterTest() {

    protected abstract val testingCodeFileName: String

    protected lateinit var expectedSuggestion: Suggestion

    override fun setUp() {
        super.setUp()
        myFixture.configureByFile(testingCodeFileName)
        expectedSuggestion = NoSuggestion
        subscribeToSuggestions { suggestion -> expectedSuggestion = suggestion }
    }

    abstract fun `testAdd one symbol to identifiers of local variable and catch suggestion`()

    abstract fun `testRemove one symbol from identifiers of local variable and catch suggestion`()

    abstract fun `testEdit identifiers of local variable using different ways of typing and removing characters and catch suggestion`()

    abstract fun `testEdit one identifier of local variable, replace old identifiers with edited identifier (using Copy+Paste) and catch suggestion`()

    abstract fun `testEdit identifiers of method and catch suggestion`()

    abstract fun `testEdit identifiers of field and catch suggestion`()

    abstract fun `testEdit identifiers of function parameter and catch suggestion`()

    abstract fun `testEdit identifiers of field but leave them unchanged and don't catch suggestion`()

    abstract fun `testEdit identifiers that references to different variables and don't catch suggestion`()

    protected fun assertSuggestedCorrectly(suggestion: Suggestion) {
        TestCase.assertTrue(suggestion is PopupSuggestion)
        TestCase.assertEquals(RenamingSuggester.POPUP_MESSAGE, (suggestion as PopupSuggestion).message)
    }
}