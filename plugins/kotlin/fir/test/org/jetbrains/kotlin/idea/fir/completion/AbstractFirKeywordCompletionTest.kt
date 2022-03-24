// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.completion

import org.jetbrains.kotlin.idea.completion.test.AbstractKeywordCompletionTest
import org.jetbrains.kotlin.test.utils.IgnoreTests
import java.io.File

abstract class AbstractFirKeywordCompletionTest : AbstractKeywordCompletionTest() {
    override val captureExceptions: Boolean = false

    override fun handleTestPath(path: String): File =
        IgnoreTests.getFirTestFileIfFirPassing(File(path), IgnoreTests.DIRECTIVES.FIR_COMPARISON)

    override fun executeTest(test: () -> Unit) {
        IgnoreTests.runTestIfEnabledByFileDirective(testDataFile().toPath(), IgnoreTests.DIRECTIVES.FIR_COMPARISON) {
            super.executeTest(test)
            IgnoreTests.cleanUpIdenticalFirTestFile(testDataFile())
        }
    }
}