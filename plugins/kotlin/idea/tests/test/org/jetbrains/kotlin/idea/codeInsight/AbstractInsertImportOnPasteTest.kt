// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.dumpTextWithErrors
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractK1InsertImportOnPasteTest : AbstractInsertImportOnPasteTest() {
    private val NO_ERRORS_DUMP_DIRECTIVE = "// NO_ERRORS_DUMP"

    override fun checkResults(expectedResultFile: File, resultFile: KtFile) {
        val testFileText = FileUtil.loadFile(dataFile(), true)
        val resultText = if (InTextDirectivesUtils.isDirectiveDefined(testFileText, NO_ERRORS_DUMP_DIRECTIVE))
            resultFile.text
        else
            resultFile.dumpTextWithErrors()
        KotlinTestUtils.assertEqualsToFile(expectedResultFile, resultText)
    }

}
