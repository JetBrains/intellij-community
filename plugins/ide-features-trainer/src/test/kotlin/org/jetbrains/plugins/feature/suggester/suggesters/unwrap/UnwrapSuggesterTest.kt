package org.jetbrains.plugins.feature.suggester.suggesters.unwrap

import org.jetbrains.plugins.feature.suggester.suggesters.FeatureSuggesterTest

abstract class UnwrapSuggesterTest : FeatureSuggesterTest() {

    abstract fun `testUnwrap IF statement and get suggestion`()

    abstract fun `testUnwrap one-line IF and get suggestion`()

    abstract fun `testUnwrap IF with deleting multiline selection and get suggestion`()

    abstract fun `testUnwrap FOR and get suggestion`()

    abstract fun `testUnwrap WHILE and get suggestion`()

    abstract fun `testUnwrap commented IF and don't get suggestion`()

    abstract fun `testUnwrap IF written in string block and don't get suggestion`()
}