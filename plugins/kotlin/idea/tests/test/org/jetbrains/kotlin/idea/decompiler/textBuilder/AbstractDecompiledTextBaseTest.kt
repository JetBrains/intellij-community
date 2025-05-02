// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.decompiler.textBuilder

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.psi.psiUtil.getElementTextWithContext
import org.jetbrains.kotlin.test.TargetBackend
import java.io.File

abstract class AbstractDecompiledTextBaseTest(
    baseDirectory: String,
) : KotlinLightCodeInsightFixtureTestCase() {
    protected companion object {
        const val TEST_PACKAGE = "test"
    }

    protected abstract fun getFileToDecompile(): VirtualFile

    protected abstract fun checkPsiFile(psiFile: PsiFile)

    protected abstract fun textToCheck(psiFile: PsiFile): String

    protected open fun checkStubConsistency(file: VirtualFile, decompiledFile: PsiFile) {}

    protected val mockSourcesBase = File(IDEA_TEST_DATA_DIR, baseDirectory)

    private lateinit var mockLibraryFacility: MockLibraryFacility

    override val testDataDirectory: File
        get() = File(mockSourcesBase, getTestName(false))

    override fun shouldRunTest(): Boolean {
        return InTextDirectivesUtils.isCompatibleTarget(TargetBackend.JVM, testDataDirectory)
    }

    override fun setUp() {
        super.setUp()

        val directivesText = InTextDirectivesUtils.textWithDirectives(testDataDirectory)
        mockLibraryFacility = MockLibraryFacility(
            source = testDataDirectory,
            attachSources = false,
            platform = KotlinCompilerStandalone.Platform.Jvm(),
            options = getCompilationOptions(directivesText),
            classpath = getCompilationClasspath(directivesText),
        )

        mockLibraryFacility.setUp(module)
    }

    private fun getCompilationOptions(directivesText: String): List<String> =
        if (InTextDirectivesUtils.isDirectiveDefined(directivesText, "ALLOW_KOTLIN_PACKAGE")) {
            listOf("-Xallow-kotlin-package")
        } else {
            emptyList()
        }

    private fun getCompilationClasspath(directivesText: String): List<File> =
        if (InTextDirectivesUtils.isDirectiveDefined(directivesText, "STDLIB_JDK_8")) {
            listOf(TestKotlinArtifacts.kotlinStdlibJdk8)
        } else {
            emptyList()
        }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { mockLibraryFacility.tearDown(module) },
            ThrowableRunnable { super.tearDown() }
        )
    }

    fun doTest(path: String) {
        val fileToDecompile = getFileToDecompile()
        val psiFile = PsiManager.getInstance(project).findFile(fileToDecompile)!!
        checkPsiFile(psiFile)

        val checkedText = textToCheck(psiFile)

        KotlinTestUtils.assertEqualsToFile(File("$path.expected.kt"), checkedText)

        checkStubConsistency(fileToDecompile, psiFile)

        checkThatFileWasParsedCorrectly(psiFile)
    }

    private fun checkThatFileWasParsedCorrectly(clsFile: PsiFile) {
        clsFile.accept(object : PsiRecursiveElementVisitor() {
            override fun visitErrorElement(element: PsiErrorElement) {
                fail("Decompiled file should not contain error elements!\n${element.getElementTextWithContext()}")
            }
        })
    }
}
