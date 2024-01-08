// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.highlighting

import org.jetbrains.kotlin.idea.highlighter.AbstractHighlightingMetaInfoTest
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import java.io.File

abstract class AbstractK2HighlightingMetaInfoTest : AbstractHighlightingMetaInfoTest() {
    private val HIGHLIGHTING_FIR_EXTENSION = "highlighting.fir"

    override fun isFirPlugin(): Boolean = true

    override fun getDefaultProjectDescriptor() = ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources()

    override fun highlightingFileNameSuffix(testKtFile: File): String {
        val fileContent = testKtFile.readText()

        return if (InTextDirectivesUtils.isDirectiveDefined(fileContent, IgnoreTests.DIRECTIVES.FIR_IDENTICAL)) {
            super.highlightingFileNameSuffix(testKtFile)
        } else {
            HIGHLIGHTING_FIR_EXTENSION
        }
    }

    override fun doTest(unused: String) {
        val testKtFile = dataFile()

        IgnoreTests.runTestIfNotDisabledByFileDirective(
            testKtFile.toPath(),
            disableTestDirective = IgnoreTests.DIRECTIVES.IGNORE_K2,
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