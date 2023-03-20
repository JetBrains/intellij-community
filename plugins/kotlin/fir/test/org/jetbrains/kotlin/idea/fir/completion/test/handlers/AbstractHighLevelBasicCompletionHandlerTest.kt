// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.completion.test.handlers

import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.completion.test.handlers.AbstractBasicCompletionHandlerTest
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.test.utils.IgnoreTests
import org.jetbrains.kotlin.test.utils.withExtension
import java.io.File

abstract class AbstractHighLevelBasicCompletionHandlerTest : AbstractBasicCompletionHandlerTest() {
    override val captureExceptions: Boolean = false

    override fun isFirPlugin(): Boolean = true

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }

    override fun handleTestPath(path: String): File =
        IgnoreTests.getFirTestFileIfFirPassing(File(path), IgnoreTests.DIRECTIVES.FIR_COMPARISON, ".after")

    override fun doTest(testPath: String) {
        IgnoreTests.runTestIfEnabledByFileDirective(dataFilePath(), IgnoreTests.DIRECTIVES.FIR_COMPARISON, ".after") {
            super.doTest(testPath)
            val originalTestFile = dataFile()
            val extension = originalTestFile.extension
            val originalAfterFile = originalTestFile.withExtension("$extension.after")
            val firAfterFile = originalTestFile.withExtension("fir.$extension.after")
            IgnoreTests.cleanUpIdenticalFirTestFile(
                originalTestFile,
                additionalFileToMarkFirIdentical = originalAfterFile,
                additionalFileToDeleteIfIdentical = firAfterFile,
                additionalFilesToCompare = listOf(originalAfterFile to firAfterFile)
            )
        }
    }
}