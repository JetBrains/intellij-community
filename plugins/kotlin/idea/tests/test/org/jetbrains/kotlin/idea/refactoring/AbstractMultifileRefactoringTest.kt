// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.jsonUtils.getNullableString
import org.jetbrains.kotlin.idea.refactoring.rename.loadTestConfiguration
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.util.prefixIfNot
import java.io.File

abstract class AbstractMultifileRefactoringTest : KotlinLightCodeInsightFixtureTestCase() {
    interface RefactoringAction {
        fun runRefactoring(rootDir: VirtualFile, mainFile: PsiFile, elementsAtCaret: List<PsiElement>, config: JsonObject)
    }

    override fun getProjectDescriptor(): LightProjectDescriptor {
        val testConfigurationFile = File(testDataDirectory, fileName())
        val config = loadTestConfiguration(testConfigurationFile)
        val withRuntime = config["withRuntime"]?.asBoolean ?: false
        if (withRuntime) {
            return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
        }
        return KotlinLightProjectDescriptor.INSTANCE
    }

    protected abstract fun runRefactoring(path: String, config: JsonObject, rootDir: VirtualFile, project: Project)

    protected open fun isEnabled(config: JsonObject): Boolean = true

    protected open fun doTest(unused: String) {
        val testFile = dataFile()
        val config = JsonParser.parseString(FileUtil.loadFile(testFile, true)) as JsonObject
        if (!isEnabled(config)) return
        doTestCommittingDocuments(testFile) { rootDir ->
            val opts = config.getNullableString("customCompilerOpts")?.prefixIfNot("// ") ?: ""
            withCustomCompilerOptions(opts, project, module) {
                runRefactoring(testFile.path, config, rootDir, project)
            }
        }
    }

    override val testDataDirectory: File
        get() {
            val name = getTestName(true).substringBeforeLast('_').replace('_', '/')
            return super.testDataDirectory.resolve(name)
        }

    override fun fileName() = testDataDirectory.name + ".test"

    private fun doTestCommittingDocuments(testFile: File, action: (VirtualFile) -> Unit) {
        val beforeVFile = myFixture.copyDirectoryToProject("before", "")
        PsiDocumentManager.getInstance(myFixture.project).commitAllDocuments()

        val afterDir = File(testFile.parentFile, "after")
        val afterVFile = LocalFileSystem.getInstance().findFileByIoFile(afterDir)?.apply {
            UsefulTestCase.refreshRecursively(this)
        } ?: error("`after` directory not found")

        action(beforeVFile)

        PsiDocumentManager.getInstance(project).commitAllDocuments()
        FileDocumentManager.getInstance().saveAllDocuments()
        PlatformTestUtil.assertDirectoriesEqual(afterVFile, beforeVFile, ::fileFilter, ::fileNameMapper)
    }

    protected open fun fileFilter(file: VirtualFile): Boolean {
        return !KotlinTestUtils.isMultiExtensionName(file.name)
    }

    protected open fun fileNameMapper(file: VirtualFile): String {
        return file.name
    }
}

fun runRefactoringTest(
    path: String,
    config: JsonObject,
    rootDir: VirtualFile,
    project: Project,
    action: AbstractMultifileRefactoringTest.RefactoringAction,
    alternativeConflicts: String? = null
) {
    val mainFilePath = config.getNullableString("mainFile") ?: config.getAsJsonArray("filesToMove").first().asString

    val conflictFile = (alternativeConflicts
        ?.let { File(File(path).parentFile, alternativeConflicts) }?.takeIf { it.exists() }
        ?: File(File(path).parentFile, "conflicts.k2.txt").takeIf { KotlinPluginModeProvider.isK2Mode() && it.exists() }
        ?: File(File(path).parentFile, "conflicts.txt")).normalize()

    val mainFile = rootDir.findFileByRelativePath(mainFilePath)!!
    val mainPsiFile = PsiManager.getInstance(project).findFile(mainFile)!!
    val document = FileDocumentManager.getInstance().getDocument(mainFile)!!
    val editor = EditorFactory.getInstance()!!.createEditor(document, project)!!

    val caretOffsets = document.extractMultipleMarkerOffsets(project)
    val elementsAtCaret = caretOffsets.map {
        TargetElementUtil.getInstance().findTargetElement(
            editor,
            TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED or TargetElementUtil.ELEMENT_NAME_ACCEPTED,
            it
        )!!
    }

    try {
        action.runRefactoring(rootDir, mainPsiFile, elementsAtCaret, config)

        assert(!conflictFile.exists()) { "Conflict file $conflictFile should not exist" }
    } catch (e: BaseRefactoringProcessor.ConflictsInTestsException) {
        KotlinTestUtils.assertEqualsToFile(conflictFile, e.messages.sorted().joinToString("\n"))

        BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts<Throwable> {
            // Run refactoring again with ConflictsInTestsException suppressed
            action.runRefactoring(rootDir, mainPsiFile, elementsAtCaret, config)
        }
    } finally {
        EditorFactory.getInstance()!!.releaseEditor(editor)
    }
}