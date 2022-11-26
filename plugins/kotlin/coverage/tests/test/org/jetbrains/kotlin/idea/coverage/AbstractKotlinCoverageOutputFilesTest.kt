// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.coverage

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractKotlinCoverageOutputFilesTest : KotlinLightCodeInsightFixtureTestCase() {
    fun doTest(unused: String) {
        val kotlinFile = myFixture.configureByFile(fileName()) as KtFile
        val outDir = myFixture.tempDirFixture.findOrCreateDir("coverageTestOut")

        val testFile = dataFile()

        try {
            val classesFile = File(testFile.parent, testFile.nameWithoutExtension + ".classes.txt")
            val expectedFile = File(testFile.parent, testFile.nameWithoutExtension + ".expected.txt")

            for (line in FileUtil.loadLines(classesFile)) {
                runWriteAction {
                    createEmptyFile(outDir, line)
                }
            }

            val actualClasses = KotlinCoverageExtension.collectGeneratedClassQualifiedNames(outDir, kotlinFile)
            KotlinTestUtils.assertEqualsToFile(expectedFile, actualClasses!!.joinToString("\n"))
        } finally {
            runWriteAction {
                outDir.delete(null)
            }
        }
    }
}

private fun createEmptyFile(dir: VirtualFile, relativePath: String) {
    var currentDir = dir
    val segments = relativePath.split('/')
    segments.forEachIndexed { i, s ->
        if (i < segments.size - 1) {
            currentDir = currentDir.createChildDirectory(null, s)
        } else {
            currentDir.createChildData(null, s)
        }
    }
}
