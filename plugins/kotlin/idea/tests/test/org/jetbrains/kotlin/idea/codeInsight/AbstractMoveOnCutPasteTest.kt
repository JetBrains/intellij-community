// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInsight.actions.OptimizeImportsProcessor
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.AbstractCopyPasteTest
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.idea.refactoring.cutPaste.MoveDeclarationsEditorCookie
import org.jetbrains.kotlin.idea.refactoring.cutPaste.MoveDeclarationsProcessor
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.dumpTextWithErrors
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractMoveOnCutPasteTest : AbstractCopyPasteTest() {
    private val OPTIMIZE_IMPORTS_AFTER_CUT_DIRECTIVE = "// OPTIMIZE_IMPORTS_AFTER_CUT"
    private val IS_AVAILABLE_DIRECTIVE = "// IS_AVAILABLE:"
    private val COPY_DIRECTIVE = "// COPY"

    protected fun doTest(unused: String) {
        val testFile = dataFile()
        val sourceFileName = testFile.name
        val testFileText = FileUtil.loadFile(testFile, true)

        val dependencyFileName = sourceFileName.replace(".kt", ".dependency.kt")
        val dependencyPsiFile = configureByDependencyIfExists(dependencyFileName) as KtFile?
        val sourcePsiFile = myFixture.configureByFile(sourceFileName) as KtFile
        val useCopy = InTextDirectivesUtils.isDirectiveDefined(testFileText, COPY_DIRECTIVE)
        val caretMarker = myFixture.editor.document.createRangeMarker(myFixture.caretOffset, myFixture.caretOffset)
        myFixture.performEditorAction(if (useCopy) IdeActions.ACTION_COPY else IdeActions.ACTION_CUT)
        myFixture.editor.moveCaret(caretMarker.startOffset)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        if (InTextDirectivesUtils.isDirectiveDefined(testFileText, OPTIMIZE_IMPORTS_AFTER_CUT_DIRECTIVE)) {
            OptimizeImportsProcessor(project, sourcePsiFile).run()
        }

        editor.putUserData(MoveDeclarationsEditorCookie.KEY, null) // because editor is reused

        val targetFileName = sourceFileName.replace(".kt", ".to.kt")
        val targetFileExists = dataFile(targetFileName).exists()
        val targetPsiFile = if (targetFileExists) configureTargetFile(targetFileName) else null
        performNotWriteEditorAction(IdeActions.ACTION_PASTE)
        UIUtil.dispatchAllInvocationEvents()

        val shouldBeAvailable = InTextDirectivesUtils.getPrefixedBoolean(testFileText, IS_AVAILABLE_DIRECTIVE) ?: true
        val cookie = editor.getUserData(MoveDeclarationsEditorCookie.KEY)
        val processor = cookie?.let { MoveDeclarationsProcessor.build(file, cookie) }

        TestCase.assertEquals(shouldBeAvailable, processor != null)

        if (processor != null) {
            processor.performRefactoring()

            PsiDocumentManager.getInstance(project).commitAllDocuments()

            if (dependencyPsiFile != null) {
                KotlinTestUtils.assertEqualsToFile(
                  dataFile(dependencyFileName.replace(".kt", ".expected.kt")),
                  dependencyPsiFile.dumpTextWithErrors()
                )
            }

            KotlinTestUtils.assertEqualsToFile(
              dataFile(sourceFileName.replace(".kt", ".expected.kt")),
              sourcePsiFile.dumpTextWithErrors()
            )
            if (targetPsiFile != null) {
                KotlinTestUtils.assertEqualsToFile(
                  dataFile(targetFileName.replace(".kt", ".expected.kt")),
                  targetPsiFile.dumpTextWithErrors()
                )
            }
        }
    }
}
