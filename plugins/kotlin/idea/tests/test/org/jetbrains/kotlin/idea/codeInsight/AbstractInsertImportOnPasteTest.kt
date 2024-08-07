// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import org.jetbrains.kotlin.idea.AbstractCopyPasteTest
import org.jetbrains.kotlin.idea.base.codeInsight.copyPaste.KotlinCopyPasteActionInfo.declarationsSuggestedToBeImported
import org.jetbrains.kotlin.idea.base.codeInsight.copyPaste.KotlinCopyPasteActionInfo.importsToBeDeleted
import org.jetbrains.kotlin.idea.base.codeInsight.copyPaste.KotlinCopyPasteActionInfo.importsToBeReviewed
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.codeInsight.copyPaste.KotlinCopyPasteCoroutineScopeService
import org.jetbrains.kotlin.idea.core.formatter.KotlinPackageEntry
import org.jetbrains.kotlin.idea.formatter.kotlinCustomSettings
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.dumpTextWithErrors
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.test.util.trimTrailingWhitespacesAndRemoveRedundantEmptyLinesAtTheEnd
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

abstract class AbstractInsertImportOnPasteTest : AbstractCopyPasteTest() {
    private val CLEAR_FILE_DIRECTIVE = "// CLEAR_FILE"
    private val TODO_INVESTIGATE_DIRECTIVE = "// TODO: Investigation is required"
    private val NO_ERRORS_DUMP_DIRECTIVE = "// NO_ERRORS_DUMP"
    private val DELETE_DEPENDENCIES_BEFORE_PASTE_DIRECTIVE = "// DELETE_DEPENDENCIES_BEFORE_PASTE"
    private val BLOCK_CODE_FRAGMENT_DIRECTIVE = "// BLOCK_CODE_FRAGMENT"
    private val NAME_COUNT_TO_USE_STAR_IMPORT_DIRECTIVE = "// NAME_COUNT_TO_USE_STAR_IMPORT:"
    private val PACKAGES_TO_USE_STAR_IMPORTS_DIRECTIVE = "// PACKAGE_TO_USE_STAR_IMPORTS:"

    private val disableTestDirective: String get() = IgnoreTests.DIRECTIVES.of(pluginMode)

    protected fun doTestCut(path: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(dataFile().toPath(), "${disableTestDirective}_CUT") {
            doTestAction(IdeActions.ACTION_CUT, path)
        }
    }

    protected fun doTestCopy(path: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(dataFile().toPath(), "${disableTestDirective}_COPY") {
            doTestAction(IdeActions.ACTION_COPY, path)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun waitForAsyncPasteCompletion() {
        if (isFirPlugin) {
            val coroutineScope = KotlinCopyPasteCoroutineScopeService.getCoroutineScope(project)
            val future = GlobalScope.future { coroutineScope.coroutineContext.job.children.toList().joinAll() }

            for (i in 0 until 60_000) {
                dispatchAllInvocationEventsInIdeEventQueue()
                try {
                    future.get(1, TimeUnit.MILLISECONDS)
                } catch (e: TimeoutException) {
                    continue
                }
            }
        } else {
            UIUtil.dispatchAllInvocationEvents()
            NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        }
    }

    private fun doTestAction(cutOrCopy: String, unused: String) {
        val testFile = dataFile()
        val testFileText = FileUtil.loadFile(testFile, true)
        val testFileName = testFile.name

        withCustomCompilerOptions(testFileText, project, module) {
            val dependencyPsiFile1 = configureByDependencyIfExists(getTestFileNameWithExtension(".dependency.kt"))
            val dependencyPsiFile2 = configureByDependencyIfExists(getTestFileNameWithExtension(".dependency.java"))

            val isCodeFragment = InTextDirectivesUtils.isDirectiveDefined(testFileText, BLOCK_CODE_FRAGMENT_DIRECTIVE)

            if (isCodeFragment) {
                val contextFileName = getTestFileNameWithExtension(".context.kt")
                val contextKtFile = configureByDependencyIfExists(contextFileName) as KtFile

                val selectedText = testFile.readText().substringAfter("<selection>").substringBefore("</selection>")
                val codeFragment = KtPsiFactory(project).createBlockCodeFragment(selectedText, contextKtFile)

                myFixture.configureFromExistingVirtualFile(codeFragment.virtualFile)
            } else {
                myFixture.configureByFile(testFileName)
            }

            myFixture.performEditorAction(cutOrCopy)

            if (InTextDirectivesUtils.isDirectiveDefined(testFileText, DELETE_DEPENDENCIES_BEFORE_PASTE_DIRECTIVE)) {
                assert(dependencyPsiFile1 != null || dependencyPsiFile2 != null)
                runWriteAction {
                    dependencyPsiFile1?.virtualFile?.delete(null)
                    dependencyPsiFile2?.virtualFile?.delete(null)
                }
            }

            if (InTextDirectivesUtils.isDirectiveDefined(testFileText, CLEAR_FILE_DIRECTIVE)) {
                myFixture.project.executeWriteCommand("") {
                    val fileDocument = myFixture.file.fileDocument
                    fileDocument.deleteString(0, fileDocument.textLength)
                }
            }

            val nameCountToUseStarImport = InTextDirectivesUtils.getPrefixedInt(testFileText, NAME_COUNT_TO_USE_STAR_IMPORT_DIRECTIVE)
            nameCountToUseStarImport?.let { file.kotlinCustomSettings.NAME_COUNT_TO_USE_STAR_IMPORT = it }

            InTextDirectivesUtils.findLinesWithPrefixesRemoved(testFileText, PACKAGES_TO_USE_STAR_IMPORTS_DIRECTIVE).forEach {
                file.kotlinCustomSettings.PACKAGES_TO_USE_STAR_IMPORTS.addEntry(KotlinPackageEntry(it.trim(), false))
            }

            val targetFile = configureTargetFile(getTestFileNameWithExtension(".to.kt"))

            val importsToBeDeletedFile = dataFile(getTestFileNameWithExtension(".imports_to_delete"))
            targetFile.importsToBeDeleted = if (importsToBeDeletedFile.exists()) {
                importsToBeDeletedFile.readLines()
            } else {
                emptyList()
            }

            performNotWriteEditorAction(IdeActions.ACTION_PASTE)
            waitForAsyncPasteCompletion()

            if (InTextDirectivesUtils.isDirectiveDefined(testFileText, TODO_INVESTIGATE_DIRECTIVE)) {
                println("File $testFile has $TODO_INVESTIGATE_DIRECTIVE")
                return@withCustomCompilerOptions
            }

            val namesToImportDump = targetFile.declarationsSuggestedToBeImported.joinToString("\n")
            KotlinTestUtils.assertEqualsToFile(dataFile(getTestFileNameWithExtension(".expected.names")), namesToImportDump)

            assertEquals(namesToImportDump, targetFile.importsToBeReviewed.joinToString("\n"))

            val expectedResultFile = dataFile(getTestFileNameWithExtension(".expected.kt"))
            val resultFile = myFixture.file as KtFile

            if (isFirPlugin) {
                assertEquals(
                    expectedResultFile.getTextWithoutErrorDirectives().trimTrailingWhitespacesAndRemoveRedundantEmptyLinesAtTheEnd(),
                    resultFile.text.trimTrailingWhitespacesAndRemoveRedundantEmptyLinesAtTheEnd()
                )
            } else {
                val resultText = if (InTextDirectivesUtils.isDirectiveDefined(testFileText, NO_ERRORS_DUMP_DIRECTIVE))
                    resultFile.text
                else
                    resultFile.dumpTextWithErrors()
                KotlinTestUtils.assertEqualsToFile(expectedResultFile, resultText)
            }
        }
    }

    private fun File.getTextWithoutErrorDirectives(): String {
        val directives = setOf("// ERROR:")

        return readLines().filterNot { line -> directives.any { line.startsWith(it) } }.joinToString("\n")
    }

    private fun getTestFileNameWithExtension(extension: String): String {
        val testFileName = dataFile().name.removeSuffix(".kt")

        if (isFirPlugin) {
            val k2Extension = IgnoreTests.FileExtension.K2
            val k2FileNameWithExtension = "$testFileName.$k2Extension$extension"
            val k2File = File(testDataDirectory, k2FileNameWithExtension)

            if (k2File.exists()) return k2FileNameWithExtension
        }

        return "$testFileName$extension"
    }
}
