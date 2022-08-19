// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.completion.test.handlers

import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.completion.test.handlers.AbstractKeywordCompletionHandlerTest
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.test.utils.IgnoreTests
import org.jetbrains.kotlin.test.utils.withExtension
import java.io.File

abstract class AbstractFirKeywordCompletionHandlerTest : AbstractKeywordCompletionHandlerTest() {
    override val captureExceptions: Boolean = false

    override fun isFirPlugin(): Boolean = true

    override fun handleTestPath(path: String): File =
        IgnoreTests.getFirTestFileIfFirPassing(File(path), IgnoreTests.DIRECTIVES.FIR_COMPARISON, ".after")

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }

    override fun doTest(testPath: String) {
        IgnoreTests.runTestIfEnabledByFileDirective(dataFilePath(), IgnoreTests.DIRECTIVES.FIR_COMPARISON, ".after") {
            super.doTest(testPath)
            val originalTestFile = dataFile()
            IgnoreTests.cleanUpIdenticalFirTestFile(
                originalTestFile,
                additionalFileToMarkFirIdentical = originalTestFile.withExtension("kt.after"),
                additionalFileToDeleteIfIdentical = originalTestFile.withExtension("fir.kt.after"),
                additionalFilesToCompare = listOf(originalTestFile.withExtension("kt.after") to originalTestFile.withExtension("fir.kt.after")),
            )
        }
    }
}