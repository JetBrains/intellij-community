// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.highlighter

import org.jetbrains.kotlin.idea.highlighter.AbstractHighlightingMetaInfoTest
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.test.utils.IgnoreTests
import java.io.File

abstract class AbstractFirHighlightingMetaInfoTest : AbstractHighlightingMetaInfoTest() {
    private val HIGHLIGHTING_FIR_EXTENSION = "highlighting.fir"

    override fun isFirPlugin(): Boolean = true

    override fun getDefaultProjectDescriptor() = ProjectDescriptorWithStdlibSources.INSTANCE

    override fun highlightingFileNameSuffix(testKtFile: File): String {
        val fileContent = testKtFile.readText()

        return if (InTextDirectivesUtils.isDirectiveDefined(fileContent, IgnoreTests.DIRECTIVES.FIR_IDENTICAL)) {
            super.highlightingFileNameSuffix(testKtFile)
        } else {
            HIGHLIGHTING_FIR_EXTENSION
        }
    }

    override fun doTest(unused: String) {
        val testKtFile = testDataFile()

        IgnoreTests.runTestIfNotDisabledByFileDirective(
            testKtFile.toPath(),
            disableTestDirective = IgnoreTests.DIRECTIVES.IGNORE_FIR,
            additionalFilesExtensions = arrayOf(HIGHLIGHTING_EXTENSION, HIGHLIGHTING_FIR_EXTENSION)
        ) {
            // warnings are not supported yet
            super.doTest(unused)

            IgnoreTests.cleanUpIdenticalFirTestFile(
                originalTestFile = testKtFile.getExpectedHighlightingFile(HIGHLIGHTING_EXTENSION),
                firTestFile = testKtFile.getExpectedHighlightingFile(HIGHLIGHTING_FIR_EXTENSION),
                additionalFileToMarkFirIdentical = testKtFile
            )
        }
    }
}