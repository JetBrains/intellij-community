// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.testFramework.utils.io.deleteRecursively
import org.jetbrains.kotlin.checkers.AbstractKotlinHighlightVisitorTest
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

abstract class AbstractHighlightingOutsideSourceSetTest : AbstractKotlinHighlightVisitorTest() {
    fun testFileOutsideSourceSet() {
        withTempDirectory { tempDirPath ->
            val tempFilePath = tempDirPath.resolve("outsideSourceSet.kt").apply {
                writeText("class Foo")
            }

            val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempFilePath)!!
            val ktFile = PsiManager.getInstance(project).findFile(virtualFile) as KtFile

            myFixture.openFileInEditor(virtualFile)
            assertFalse("File outside source set shouldn't be highlighted", ProblemHighlightFilter.shouldHighlightFile(ktFile))
        }
    }

    private fun withTempDirectory(block: (Path) -> Unit) {
        val path = Files.createTempDirectory("test")
        try {
            block(path)
        } finally {
            path.deleteRecursively()
        }
    }
}