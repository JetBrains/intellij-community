// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.completion

import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.completion.test.AbstractJvmBasicCompletionTestBase
import org.jetbrains.kotlin.idea.completion.test.ExpectedCompletionUtils
import org.jetbrains.kotlin.idea.completion.test.firFileName
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.test.utils.IgnoreTests

abstract class AbstractK2JvmBasicCompletionTest : AbstractJvmBasicCompletionTestBase() {
    override val captureExceptions: Boolean = false

    override fun isFirPlugin(): Boolean = true

    override val ignoreProperties: Collection<String> =
        listOf(ExpectedCompletionUtils.CompletionProposal.PRESENTATION_TEXT_ATTRIBUTES)

    override fun fileName(): String = firFileName(super.fileName(), testDataDirectory)

    override fun executeTest(test: () -> Unit) {
        IgnoreTests.runTestIfEnabledByFileDirective(dataFile().toPath(), IgnoreTests.DIRECTIVES.FIR_COMPARISON) {
            super.executeTest(test)
            IgnoreTests.cleanUpIdenticalFirTestFile(dataFile())
        }
    }

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() }
        )
    }
}