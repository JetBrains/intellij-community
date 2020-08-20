package org.jetbrains.plugins.feature.suggester.suggesters.surroundWith

import org.jetbrains.plugins.feature.suggester.suggesters.FeatureSuggesterTest

abstract class SurroundWithSuggesterTest : FeatureSuggesterTest() {

    abstract fun `testSurround one statement with IF and get suggestion`()

    abstract fun `testSurround 2 statements with IF and add '}' at the line with second statement and get suggestion`()

    abstract fun `testSurround all statements in block with IF and get suggestion`()

    abstract fun `testSurround one statement with IF in one line and get suggestion`()

    abstract fun `testSurround statements with FOR and get suggestion`()

    abstract fun `testSurround statements with WHILE and get suggestion`()

    abstract fun `testSurround 0 statements with IF and don't get suggestion`()

    abstract fun `testWrite if() but add braces in another place and don't get suggestion`()
}