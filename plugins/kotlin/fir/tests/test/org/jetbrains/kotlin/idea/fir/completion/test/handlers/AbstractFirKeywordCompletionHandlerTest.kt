// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.completion.test.handlers

import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.k2FileName
import org.jetbrains.kotlin.idea.completion.test.handlers.AbstractKeywordCompletionHandlerTest
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.test.utils.withExtension

abstract class AbstractFirKeywordCompletionHandlerTest : AbstractKeywordCompletionHandlerTest() {
    override val captureExceptions: Boolean = false

    override fun isFirPlugin(): Boolean = true

    override fun fileName(): String = k2FileName(super.fileName(), testDataDirectory, IgnoreTests.FileExtension.FIR, ".after")

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }

    override fun doTest(testPath: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(dataFilePath(), IgnoreTests.DIRECTIVES.IGNORE_K2, ".after") {
            super.doTest(testPath)
            val originalTestFile = dataFile()
            IgnoreTests.cleanUpIdenticalK2TestFile(
                originalTestFile,
                k2Extension = IgnoreTests.FileExtension.FIR,
                additionalFileToMarkFirIdentical = originalTestFile.withExtension("kt.after"),
                additionalFileToDeleteIfIdentical = originalTestFile.withExtension("fir.kt.after"),
                additionalFilesToCompare = listOf(originalTestFile.withExtension("kt.after") to originalTestFile.withExtension("fir.kt.after")),
            )
        }
    }
}