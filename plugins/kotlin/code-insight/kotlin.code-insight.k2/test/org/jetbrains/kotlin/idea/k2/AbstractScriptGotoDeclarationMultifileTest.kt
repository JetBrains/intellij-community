// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2

import com.intellij.codeInsight.daemon.impl.EditorTracker
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiFile
import com.intellij.testFramework.GlobalState
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.core.script.k2.highlighting.DefaultScriptResolutionStrategy
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.idea.test.KotlinBaseTest.TestFile
import org.jetbrains.kotlin.psi.KtFile
import org.junit.jupiter.api.Assertions
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.readText

const val EXPECTED_TEXT: String = "EXPECTED_TEXT"

abstract class AbstractScriptGotoDeclarationMultifileTest : KotlinMultiFileLightCodeInsightFixtureTestCase() {
    override fun runInDispatchThread(): Boolean = false

    protected val document: Document
        get() {
            val fileEditorManager = FileEditorManager.getInstance(project) as FileEditorManagerEx
            return fileEditorManager.selectedTextEditor?.document ?: error("no document found")
        }

    override fun doMultiFileTest(files: List<PsiFile>, globalDirectives: Directives) {
        val expectedText = globalDirectives[EXPECTED_TEXT]
        Assertions.assertNotNull(expectedText, "$EXPECTED_TEXT directive not found")
        expectedText ?: return

        val mainFile = files.first() as KtFile

        runInEdtAndWait {
            myFixture.configureFromExistingVirtualFile(mainFile.virtualFile)
        }

        runBlocking {
            DefaultScriptResolutionStrategy.getInstance(project).execute(*(files.mapNotNull { it as? KtFile }.toTypedArray())).join()
        }

        runInEdtAndWait {
            myFixture.checkHighlighting()
        }

        runInEdtAndWait {
            myFixture.performEditorAction(IdeActions.ACTION_GOTO_DECLARATION)
        }

        val text = document.text
        Assertions.assertTrue(text.startsWith(expectedText), "Actual text:\n\n$text")
    }

    override fun setUp() {
        GlobalState.checkSystemStreams()
        setupTempDir()

        setUpWithKotlinPlugin {
            val projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name)
            myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture())
            myFixture.setTestDataPath(testDataDirectory.path)
            myFixture.setUp()
        }

        VfsRootAccess.allowRootAccess(myFixture.testRootDisposable, KotlinRoot.DIR.path)
        EditorTracker.getInstance(project)
        invalidateLibraryCache(project)
    }

    override fun doTest(testDataPath: String) {
        val testKtFile = dataFile()

        IgnoreTests.runTestIfNotDisabledByFileDirective(
            testKtFile.toPath(),
            disableTestDirective = IgnoreTests.DIRECTIVES.IGNORE_K2,
        ) {
            val testFile = Path(testDataPath)

            val subFiles: List<TestFile> = createTestFiles(testFile)

            val files = configureMultiFileTest(subFiles)
            doMultiFileTest(
                testDataPath = testDataPath,
                files = files,
                globalDirectives = files.firstOrNull()?.testFile?.directives ?: Directives(),
            )
        }
    }

    private fun createTestFiles(mainFile: Path): List<TestFile> = TestFiles.createTestFiles(
        null,
        mainFile.readText(),
        object : TestFiles.TestFileFactoryNoModules<TestFile>() {
            override fun create(fileName: String, text: String, directives: Directives): TestFile {
                val linesWithoutDirectives = text.lines().filter { !it.startsWith("//") }
                return TestFile(fileName, linesWithoutDirectives.joinToString(separator = "\n"), directives)
            }
        },
    )

    private fun configureMultiFileTest(subFiles: List<TestFile>): List<TestFileWithVirtualFile> {
        return subFiles.map { TestFileWithVirtualFile(it, this.createTestFile(it)) }
    }

    private fun createTestFile(testFile: TestFile): VirtualFile {
        return myFixture.tempDirFixture.createFile(testFile.name, testFile.content)
    }
}