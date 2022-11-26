// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.idea.AbstractCopyPasteTest
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.dumpTextWithErrors
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractInsertImportOnPasteTest : AbstractCopyPasteTest() {
    private val TODO_INVESTIGATE_DIRECTIVE = "// TODO: Investigation is required"
    private val NO_ERRORS_DUMP_DIRECTIVE = "// NO_ERRORS_DUMP"
    private val DELETE_DEPENDENCIES_BEFORE_PASTE_DIRECTIVE = "// DELETE_DEPENDENCIES_BEFORE_PASTE"

    protected fun doTestCut(path: String) {
        doTestAction(IdeActions.ACTION_CUT, path)
    }

    protected fun doTestCopy(path: String) {
        doTestAction(IdeActions.ACTION_COPY, path)
    }

    private fun doTestAction(cutOrCopy: String, unused: String) {
        val testFile = dataFile()
        val testFileText = FileUtil.loadFile(testFile, true)
        val testFileName = testFile.name

        val dependencyPsiFile1 = configureByDependencyIfExists(testFileName.replace(".kt", ".dependency.kt"))
        val dependencyPsiFile2 = configureByDependencyIfExists(testFileName.replace(".kt", ".dependency.java"))
        myFixture.configureByFile(testFileName)
        myFixture.performEditorAction(cutOrCopy)

        if (InTextDirectivesUtils.isDirectiveDefined(testFileText, DELETE_DEPENDENCIES_BEFORE_PASTE_DIRECTIVE)) {
            assert(dependencyPsiFile1 != null || dependencyPsiFile2 != null)
            runWriteAction {
                dependencyPsiFile1?.virtualFile?.delete(null)
                dependencyPsiFile2?.virtualFile?.delete(null)
            }
        }

        KotlinCopyPasteReferenceProcessor.declarationsToImportSuggested = emptyList()
        ReviewAddedImports.importsToBeReviewed = emptyList()

        val importsToBeDeletedFile = dataFile(testFileName.replace(".kt", ".imports_to_delete"))
        ReviewAddedImports.importsToBeDeleted = if (importsToBeDeletedFile.exists()) {
            importsToBeDeletedFile.readLines()
        } else {
            emptyList()
        }

        configureTargetFile(testFileName.replace(".kt", ".to.kt"))
        performNotWriteEditorAction(IdeActions.ACTION_PASTE)

        UIUtil.dispatchAllInvocationEvents()
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()

        if (InTextDirectivesUtils.isDirectiveDefined(testFileText, TODO_INVESTIGATE_DIRECTIVE)) {
            println("File $testFile has $TODO_INVESTIGATE_DIRECTIVE")
            return
        }

        val namesToImportDump = KotlinCopyPasteReferenceProcessor.declarationsToImportSuggested.joinToString("\n")
        KotlinTestUtils.assertEqualsToFile(dataFile(testFileName.replace(".kt", ".expected.names")), namesToImportDump)
        assertEquals(namesToImportDump, ReviewAddedImports.importsToBeReviewed.joinToString("\n"))

        val resultFile = myFixture.file as KtFile
        val resultText = if (InTextDirectivesUtils.isDirectiveDefined(testFileText, NO_ERRORS_DUMP_DIRECTIVE))
            resultFile.text
        else
            resultFile.dumpTextWithErrors()
        KotlinTestUtils.assertEqualsToFile(dataFile(testFileName.replace(".kt", ".expected.kt")), resultText)
    }
}
