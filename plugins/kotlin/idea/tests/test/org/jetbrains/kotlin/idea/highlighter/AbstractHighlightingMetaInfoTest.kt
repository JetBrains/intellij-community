// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiFileEx
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.KotlinMultiFileLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.kmp.KMPProjectDescriptorTestUtilities
import org.jetbrains.kotlin.idea.test.kmp.KMPTest
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractHighlightingMetaInfoTest : KotlinMultiFileLightCodeInsightFixtureTestCase() {
    protected val HIGHLIGHTING_EXTENSION = "highlighting"

    override fun doMultiFileTest(files: List<PsiFile>, globalDirectives: Directives) {
        val expectedHighlighting = dataFile().getExpectedHighlightingFile()
        val psiFile = files.first()
        if (psiFile is KtFile && psiFile.isScript()) {
            ScriptConfigurationManager.updateScriptDependenciesSynchronously(psiFile)
        }

        if (this is KMPTest) {
            KMPProjectDescriptorTestUtilities.validateTest(files, testPlatform)
        }

        files.forEach {
            if (InTextDirectivesUtils.isDirectiveDefined(it.text, "BATCH_MODE")) {
                it.putUserData(PsiFileEx.BATCH_REFERENCE_PROCESSING, true)
            }
        }

        checkHighlighting(psiFile, expectedHighlighting, globalDirectives, project)
    }

    protected fun File.getExpectedHighlightingFile(suffix: String = highlightingFileNameSuffix(this)): File {
        return resolveSibling("$name.$suffix")
    }

    protected open fun highlightingFileNameSuffix(ktFilePath: File): String = HIGHLIGHTING_EXTENSION
}