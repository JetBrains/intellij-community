// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.highlighting

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.highlighter.AbstractHighlightingMetaInfoTest
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.idea.test.kmp.KMPProjectDescriptorTestUtilities
import org.jetbrains.kotlin.idea.test.kmp.KMPTest
import org.jetbrains.kotlin.idea.test.kmp.KMPTestPlatform
import java.io.File

abstract class AbstractK2HighlightingMetaInfoTest : AbstractHighlightingMetaInfoTest(), KMPTest {

    @Deprecated("Use HIGHLIGHTING_K2_EXTENSION")
    protected val HIGHLIGHTING_FIR_EXTENSION = "highlighting.fir"
    protected val HIGHLIGHTING_K2_EXTENSION = "highlighting.k2"

    override fun getDefaultProjectDescriptor() = ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources()

    override fun getProjectDescriptor(): LightProjectDescriptor =
        KMPProjectDescriptorTestUtilities.createKMPProjectDescriptor(testPlatform)
            ?: super.getProjectDescriptor()

    override val testPlatform: KMPTestPlatform
        get() = KMPTestPlatform.Unspecified

    override fun highlightingFileNameSuffix(testKtFile: File): String {
        val fileContent = testKtFile.readText()

        return if (InTextDirectivesUtils.isDirectiveDefined(fileContent, IgnoreTests.DIRECTIVES.FIR_IDENTICAL)) {
            super.highlightingFileNameSuffix(testKtFile)
        } else {
            HIGHLIGHTING_K2_EXTENSION
        }
    }

    override fun doTest(unused: String) {
        val testKtFile = dataFile()

        IgnoreTests.runTestIfNotDisabledByFileDirective(
            testKtFile.toPath(),
            disableTestDirective = IgnoreTests.DIRECTIVES.IGNORE_K2,
            additionalFilesExtensions = arrayOf(HIGHLIGHTING_EXTENSION, HIGHLIGHTING_K2_EXTENSION)
        ) {
            // warnings are not supported yet
            super.doTest(unused)

            IgnoreTests.cleanUpIdenticalK2TestFile(
                originalTestFile = testKtFile.getExpectedHighlightingFile(HIGHLIGHTING_EXTENSION),
                k2Extension = IgnoreTests.FileExtension.FIR,
                k2TestFile = testKtFile.getExpectedHighlightingFile(HIGHLIGHTING_K2_EXTENSION),
                additionalFileToMarkFirIdentical = testKtFile
            )
        }
    }
}