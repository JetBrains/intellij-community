// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.completion.test.handlers

import org.jetbrains.kotlin.idea.completion.test.handlers.AbstractKeywordCompletionHandlerTest
import org.jetbrains.kotlin.test.utils.IgnoreTests
import org.jetbrains.kotlin.test.utils.withExtension
import java.io.File

abstract class AbstractFirKeywordCompletionHandlerTest : AbstractKeywordCompletionHandlerTest() {
    override val captureExceptions: Boolean = false

    override fun handleTestPath(path: String): File =
        IgnoreTests.getFirTestFileIfFirPassing(File(path), IgnoreTests.DIRECTIVES.FIR_COMPARISON, ".after")

    override fun doTest(testPath: String) {
        IgnoreTests.runTestIfEnabledByFileDirective(testDataFilePath(), IgnoreTests.DIRECTIVES.FIR_COMPARISON, ".after") {
            super.doTest(testPath)
            val originalTestFile = testDataFile()
            IgnoreTests.cleanUpIdenticalFirTestFile(
                originalTestFile,
                additionalFileToMarkFirIdentical = originalTestFile.withExtension("kt.after"),
                additionalFileToDeleteIfIdentical = originalTestFile.withExtension("fir.kt.after")
            )
        }
    }
}