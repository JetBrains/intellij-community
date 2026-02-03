// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.script

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.idea.base.codeInsight.copyPaste.KotlinCopyPasteActionInfo.declarationsSuggestedToBeImported
import org.jetbrains.kotlin.idea.base.codeInsight.copyPaste.KotlinCopyPasteActionInfo.importsToBeDeleted
import org.jetbrains.kotlin.idea.base.codeInsight.copyPaste.KotlinCopyPasteActionInfo.importsToBeReviewed
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.dumpTextWithErrors
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractScriptConfigurationInsertImportOnPasteTest : AbstractScriptConfigurationTest() {
    private val NO_ERRORS_DUMP_DIRECTIVE = "// NO_ERRORS_DUMP"
    private val DELETE_DEPENDENCIES_BEFORE_PASTE_DIRECTIVE = "// DELETE_DEPENDENCIES_BEFORE_PASTE"

    protected fun doTestCut(path: String) {
        doTestAction(IdeActions.ACTION_CUT, path)
    }

    protected fun doTestCopy(path: String) {
        doTestAction(IdeActions.ACTION_COPY, path)
    }

    private fun doTestAction(cutOrCopy: String, unused: String) {
        val testDataFile = testDataFile()
        val testFile = File(testDataFile, "script.kts")
        val testFileText = FileUtil.loadFile(testFile, true)
        val noErrorsDump = InTextDirectivesUtils.isDirectiveDefined(testFileText, NO_ERRORS_DUMP_DIRECTIVE)

        val scriptFile = configureScriptFile(File(testDataFile.path))
        if (!noErrorsDump) {
            val sourceFile = scriptFile.toKtFile()
            val dumpTextWithErrors = sourceFile.dumpTextWithErrors()
            assertEquals(sourceFile.text, dumpTextWithErrors)
        }

        performEditorAction(cutOrCopy)

        val targetScript = createFileAndSyncDependencies(File(testDataFile, "target.kts"))
        val targetFile = targetScript.toKtFile()

        val importsToBeDeletedFile = File(testDataFile, "imports_to_delete")
        targetFile.importsToBeDeleted = if (importsToBeDeletedFile.exists()) {
            importsToBeDeletedFile.readLines()
        } else {
            emptyList()
        }

        performEditorAction(IdeActions.ACTION_PASTE)
        UIUtil.dispatchAllInvocationEvents()

        val namesToImportDump = targetFile.declarationsSuggestedToBeImported.joinToString("\n")
        KotlinTestUtils.assertEqualsToFile(File(testDataFile, "expected.names"), namesToImportDump)
        assertEquals(namesToImportDump, targetFile.importsToBeReviewed.joinToString("\n"))

        val sourceText = if (noErrorsDump)
            targetFile.text
        else
            targetFile.dumpTextWithErrors()
        KotlinTestUtils.assertEqualsToFile(File(testDataFile, "expected.kts"), sourceText)
    }

    private fun VirtualFile.toKtFile() = PsiManager.getInstance(project).findFile(this) as KtFile

    private fun performEditorAction(actionId: String): Boolean {
        val dataContext = getEditorDataContext()
        val managerEx = ActionManagerEx.getInstanceEx()
        val action = managerEx.getAction(actionId)
        val event = AnActionEvent.createEvent(dataContext, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
        ActionUtil.updateAction(action, event)
        if (event.presentation.isEnabled) {
            ActionUtil.performAction(action, event)
            return true
        }
        return false
    }

    private fun getEditorDataContext() = (editor as EditorEx).dataContext

}