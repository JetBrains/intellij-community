// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.groovy.k2

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.groovy.GroovyLibraryDependenciesToBuildGradleKtsCopyPastePreprocessor
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import java.io.File

abstract class AbstractGroovyLibraryDependenciesToBuildGradleKtsCopyPastePreprocessorTest : KotlinLightCodeInsightFixtureTestCase() {
    fun doTest(path: String) {
        val file = File(path)
        val text = FileUtil.loadFile(file, true)
        val buildGradleKtsToPaste = myFixture.configureByText("build.gradle.kts", "")
        val actual = performPast(buildGradleKtsToPaste, text) ?: "// <NOT CONVERTED>"

        val afterFile = file.resolveSibling(file.nameWithoutExtension + ".after.kts")
        if (!afterFile.exists()) {
            afterFile.writeText(actual)
            fail("File ${afterFile.name} was not found. New file was created (${afterFile.path}).")
        }
        KotlinTestUtils.assertEqualsToFile(afterFile, actual)
    }

    private fun performPast(buildGradleKtsToPaste: PsiFile, text: String): String? {
        val result = GroovyLibraryDependenciesToBuildGradleKtsCopyPastePreprocessor()
            .preprocessOnPaste(myFixture.project, buildGradleKtsToPaste, myFixture.editor, text, null)
        if (result == text) {
            // no processing was made
            return null
        }
        return result
    }
}