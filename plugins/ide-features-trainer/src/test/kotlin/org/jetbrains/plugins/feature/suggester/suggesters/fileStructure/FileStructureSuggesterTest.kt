package org.jetbrains.plugins.feature.suggester.suggesters.fileStructure

import org.jetbrains.plugins.feature.suggester.suggesters.FeatureSuggesterTest

abstract class FileStructureSuggesterTest : FeatureSuggesterTest() {

    abstract fun `testFind field and get suggestion`()

    abstract fun `testFind method and get suggestion`()

    abstract fun `testFind function parameter and don't get suggestion`()

    abstract fun `testFind local variable declaration and don't get suggestion`()

    abstract fun `testFind variable usage and don't get suggestion`()

    abstract fun `testFind method usage and don't get suggestion`()

    abstract fun `testFind type usage and don't get suggestion`()
}
