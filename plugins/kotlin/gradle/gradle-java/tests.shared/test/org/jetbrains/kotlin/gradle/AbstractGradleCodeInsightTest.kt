// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.utils.vfs.getPsiFile
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.*
import org.jetbrains.kotlin.idea.test.TestFiles
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import java.io.File
import java.lang.reflect.Method

abstract class AbstractGradleCodeInsightTest: AbstractKotlinGradleCodeInsightBaseTest() {

    protected open val filesBasedTest: Boolean = true

    @TestDisposable
    protected lateinit var testRootDisposable: Disposable

    protected lateinit var testInfo: TestInfo

    @BeforeEach
    fun init(testInfo: TestInfo) {
        this.testInfo = testInfo
    }

    private var _testDataFiles: List<TestFile>? = null
    val testDataFiles: List<TestFile>
        get() = requireNotNull(_testDataFiles) {
            "testDataFiles have not been setup. Please use [AbstractGradleCodeInsightBaseTestCase.test] function inside your tests."
        }

    val mainTestDataFile: TestFile
        get() = requireNotNull(testDataFiles.firstOrNull()) {
            "expected at lead one testDataFiles."
        }

    val mainTestDataPsiFile: PsiFile
        get() = runReadAction { getFile(mainTestDataFile.path).getPsiFile(project) }

    override fun setUp() {
        super.setUp()

        loadTestDataFiles()
        testDataFiles.forEach {
            writeTextAndCommit(it.path, it.content)
        }
    }

    override fun tearDown() {
        runAll(
            { _testDataFiles = null },
            { super.tearDown() }
        )
    }

    open fun getTestDataDirectory(testName: String): File {
        val clazz = this::class.java
        val root = getTestsRoot(clazz)

        if (filesBasedTest) {
            return File(root)
        }

        val test = getTestDataFileName(clazz, testName) ?: error("No @TestMetadata for ${clazz.name}")
        return File(root, test)
    }

    fun getTestDataPath(): String = toSlashEndingDirPath(getTestDataDirectory(retrieveTestMethod().name).path)

    private fun retrieveTestMethod(): Method = testInfo.testMethod.get()

    protected fun dataFile(fileName: String): File = File(getTestDataPath(), fileName)

    protected fun dataFile(): File = dataFile(fileName())

    protected open fun fileName(): String = getMethodMetadata(testInfo.testMethod.get()) ?: error("no @TestMetadata")

    protected val document: Document
        get() {
            val fileEditorManager = FileEditorManager.getInstance(project) as FileEditorManagerEx
            return fileEditorManager.selectedTextEditor?.document ?: error("no document found")
        }

    private fun loadTestDataFiles() {
        val mainFile = dataFile()
        val multiFileText = FileUtil.loadFile(mainFile, true)

        _testDataFiles = TestFiles.createTestFiles(
            mainFile.name,
            multiFileText,
            object : TestFiles.TestFileFactoryNoModules<TestFile>() {
                override fun create(fileName: String, text: String, directives: Directives): TestFile {
                    val linesWithoutDirectives = text.lines().filter { !it.startsWith("// FILE") }
                    return TestFile(fileName, linesWithoutDirectives.joinToString(separator = "\n"))
                }
            }
        )
    }

    class TestFile internal constructor(val path: String, val content: String)
}