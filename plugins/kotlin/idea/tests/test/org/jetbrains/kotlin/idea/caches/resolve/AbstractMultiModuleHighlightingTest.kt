// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.codeInsight.AbstractLineMarkersTest
import org.jetbrains.kotlin.idea.multiplatform.setupMppProjectFromDirStructure
import org.jetbrains.kotlin.idea.stubs.AbstractMultiHighlightingTest
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.allJavaFiles
import org.jetbrains.kotlin.idea.test.allKotlinFiles
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractMultiModuleHighlightingTest : AbstractMultiHighlightingTest() {

    protected open fun checkLineMarkersInProject(
        findFiles: () -> List<PsiFile> = { project.allKotlinFiles().excludeByDirective() }
    ) {
        checkFiles(findFiles) {
            checkHighlighting(myEditor, true, false)

            val markers = DaemonCodeAnalyzerImpl.getLineMarkers(getDocument(file), project)
            AbstractLineMarkersTest.assertNavigationElements(project, myFile as KtFile, myEditor, markers)
        }
    }

    protected open fun checkHighlightingInProject(
        findFiles: () -> List<PsiFile> = { project.allKotlinFiles().excludeByDirective() }
    ) {
        checkFiles(findFiles) {
            checkHighlighting(myEditor, true, false)
        }
    }
}

abstract class AbstractMultiPlatformHighlightingTest : AbstractMultiModuleHighlightingTest() {
    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR.resolve("multiModuleHighlighting/multiplatform")

    protected open fun doTest(path: String) {
        setupMppProjectFromDirStructure(File(path))
        checkHighlightingInProject {
            (project.allKotlinFiles() + project.allJavaFiles()).excludeByDirective()
        }
    }
}

private fun List<PsiFile>.excludeByDirective() = filter { !it.text.contains("// !CHECK_HIGHLIGHTING") }