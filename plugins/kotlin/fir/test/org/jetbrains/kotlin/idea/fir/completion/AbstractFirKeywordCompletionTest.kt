// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.completion

import org.jetbrains.kotlin.idea.completion.test.AbstractKeywordCompletionTest
import org.jetbrains.kotlin.idea.completion.test.firFileName
import org.jetbrains.kotlin.test.utils.IgnoreTests

abstract class AbstractFirKeywordCompletionTest : AbstractKeywordCompletionTest() {
    override val captureExceptions: Boolean = false

    override fun isFirPlugin(): Boolean = true

    override fun fileName(): String = firFileName(super.fileName(), testDataDirectory)

    override fun executeTest(test: () -> Unit) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(dataFile().toPath(), IgnoreTests.DIRECTIVES.IGNORE_K2) {
            super.executeTest(test)
            IgnoreTests.cleanUpIdenticalFirTestFile(dataFile())
        }
    }
}