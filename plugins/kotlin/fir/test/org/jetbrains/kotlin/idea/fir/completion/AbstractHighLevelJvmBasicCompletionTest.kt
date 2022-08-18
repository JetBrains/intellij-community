// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.completion

import org.jetbrains.kotlin.idea.completion.test.AbstractJvmBasicCompletionTest
import org.jetbrains.kotlin.idea.completion.test.ExpectedCompletionUtils
import org.jetbrains.kotlin.test.utils.IgnoreTests
import java.io.File

abstract class AbstractHighLevelJvmBasicCompletionTest : AbstractJvmBasicCompletionTest() {
    override val captureExceptions: Boolean = false

    override fun isFirPlugin(): Boolean = true

    override val ignoreProperties: Collection<String> =
        listOf(ExpectedCompletionUtils.CompletionProposal.PRESENTATION_TEXT_ATTRIBUTES)

    override fun handleTestPath(path: String): File =
        IgnoreTests.getFirTestFileIfFirPassing(File(path), IgnoreTests.DIRECTIVES.FIR_COMPARISON)

    override fun executeTest(test: () -> Unit) {
        IgnoreTests.runTestIfEnabledByFileDirective(dataFile().toPath(), IgnoreTests.DIRECTIVES.FIR_COMPARISON) {
            super.executeTest(test)
            IgnoreTests.cleanUpIdenticalFirTestFile(dataFile())
        }
    }
}