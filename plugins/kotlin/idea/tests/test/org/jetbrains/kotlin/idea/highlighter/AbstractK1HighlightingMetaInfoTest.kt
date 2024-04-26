// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

import java.io.File
import org.jetbrains.kotlin.idea.base.test.IgnoreTests

abstract class AbstractK1HighlightingMetaInfoTest : AbstractHighlightingMetaInfoTest() {
    override fun doTest(unused: String) {
        val testKtFile = dataFile()

        IgnoreTests.runTestIfNotDisabledByFileDirective(
            testKtFile.toPath(),
            disableTestDirective = IgnoreTests.DIRECTIVES.IGNORE_K1,
            additionalFilesExtensions = arrayOf(HIGHLIGHTING_EXTENSION)
        ) {
            super.doTest(unused)
        }
    }
}
