// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.codeInsight.gradle.AbstractGradleCodeInsightTest.TestFile
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.getTestDataFileName
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.getTestsRoot
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.toSlashEndingDirPath
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import java.io.File
import java.nio.charset.StandardCharsets

private const val SCRIPTING_ENABLED_FLAG = "kotlin.k2.scripting.enabled"

abstract class AbstractGradleCodeInsightTest: GradleImportingTestCase() {

    protected open val filesBasedTest: Boolean = true

    open fun getTestDataDirectory(): File {
        val clazz = this::class.java
        val root = getTestsRoot(clazz)

        if (filesBasedTest) {
            return File(root)
        }

        val test = getTestDataFileName(clazz, getName()) ?: error("No @TestMetadata for ${clazz.name}")
        return File(root, test)
    }

    fun getTestDataPath(): String = toSlashEndingDirPath(getTestDataDirectory().path)

    protected fun dataFile(fileName: String): File = File(getTestDataPath(), fileName)

    protected fun dataFile(): File = dataFile(fileName())

    protected open fun fileName(): String = getTestDataFileName(this::class.java, getName()) ?: (getTestName(false) + ".kt")

    // fixtures

    private var codeInsightTestFixture: CodeInsightTestFixture? = null

    protected val fixture: CodeInsightTestFixture
        get() = codeInsightTestFixture ?: error("codeInsightTestFixture not initialized")

    protected val document: Document
        get() {
            val fileEditorManager = FileEditorManager.getInstance(fixture.project) as FileEditorManagerEx
            return fileEditorManager.selectedTextEditor?.document ?: error("no document found")
        }

    override fun setUpFixtures() {
        myTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName()).fixture
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(myTestFixture)
        fixture.setUp()
        codeInsightTestFixture = fixture
    }

    override fun collectAllowedRoots(roots: MutableList<String>) {
        super.collectAllowedRoots(roots)
        roots += fixture.tempDirPath
    }

    override fun setUp() {
        gradleVersion = "8.6"
        Registry.get(SCRIPTING_ENABLED_FLAG).setValue(true)

        super.setUp()

        val mainFile = configureByFiles().firstOrNull() ?: error("main file not found")
        fixture.configureFromExistingVirtualFile(mainFile)
        importProjectWithMainFile(mainFile)
    }

    override fun tearDownFixtures() {
        runAll(
            ThrowableRunnable { fixture.tearDown() },
            ThrowableRunnable {
                codeInsightTestFixture = null
            },
            ThrowableRunnable { myTestFixture = null }
        )
    }

    fun configureByFiles(): List<VirtualFile> {
        val mainFile = dataFile()
        val multiFileText = FileUtil.loadFile(mainFile, true)

        val subFiles = TestFiles.createTestFiles(
            mainFile.name,
            multiFileText,
            object : TestFiles.TestFileFactoryNoModules<TestFile>() {
                override fun create(fileName: String, text: String, directives: Directives): TestFile {
                    val linesWithoutDirectives = text.lines().filter { !it.startsWith("// FILE") }
                    return TestFile(fileName, linesWithoutDirectives.joinToString(separator = "\n"))
                }
            }
        )
        val files = mutableListOf<VirtualFile>()
        runInEdtAndWait { files += subFiles.map<TestFile, VirtualFile>(this::createTestFile) }
        return files
    }

    private fun createTestFile(testFile: TestFile): VirtualFile {
        return runWriteAction {
            val path = testFile.path
            val vFile = createProjectSubFile(path)
            vFile.charset = StandardCharsets.UTF_8
            VfsUtil.saveText(vFile, testFile.content)
            vFile.putUserData(VfsTestUtil.TEST_DATA_FILE_PATH, path)
            vFile
        }
    }

    fun importProjectWithMainFile(mainFile: VirtualFile) {
        val offset = runReadAction { fixture.editor.caretModel.offset }

        importProject(false)

        fixture.configureFromExistingVirtualFile(mainFile)

        // restore caret, selection is not supported yet
        // see com.intellij.testFramework.EditorTestUtil.extractCaretAndSelectionMarkers(com.intellij.openapi.editor.Document)
        runInEdtAndWait {
            fixture.editor.caretModel.moveToOffset(offset)
        }
    }

    class TestFile internal constructor(val path: String, val content: String)
}