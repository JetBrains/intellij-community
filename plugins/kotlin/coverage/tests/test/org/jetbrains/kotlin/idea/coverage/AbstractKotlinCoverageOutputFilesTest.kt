// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.coverage

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

abstract class AbstractKotlinCoverageOutputFilesTest : KotlinLightCodeInsightFixtureTestCase() {
    fun doTest(unused: String) {
        val kotlinFile = myFixture.configureByFile(fileName()) as KtFile
        val outDirPath = Files.createTempDirectory("coverageTestOut")

        val testFile = dataFile()

        try {
            val classesFile = File(testFile.parent, testFile.nameWithoutExtension + ".classes.txt")
            val expectedFile = File(testFile.parent, testFile.nameWithoutExtension + ".expected.txt")

            for (line in Files.readAllLines(classesFile.toPath())) {
                createEmptyFile(outDirPath, line)
            }

            val outDir = VirtualFileManager.getInstance().refreshAndFindFileByUrl(VfsUtilCore.pathToUrl(outDirPath.toString()))!!
            val actualClasses = KotlinCoverageExtension.collectGeneratedClassQualifiedNames(outDir, kotlinFile)
            KotlinTestUtils.assertEqualsToFile(expectedFile, actualClasses!!.sortedBy { it.replace('$', '.') }.joinToString("\n"))
        } finally {
            deleteRecursively(outDirPath)
        }
    }
}

private fun createEmptyFile(dir: Path, relativePath: String) {
    val file = dir.resolve(relativePath)
    Files.createDirectories(file.parent)
    Files.createFile(file)
}

private fun deleteRecursively(path: Path) {
    if (!Files.exists(path)) return
    Files.walk(path).use { children ->
        children.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }
}
