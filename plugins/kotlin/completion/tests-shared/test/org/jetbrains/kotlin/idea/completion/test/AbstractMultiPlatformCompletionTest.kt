// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.test

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.multiplatform.setupMppProjectFromDirStructure
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.extractMarkerOffset
import org.jetbrains.kotlin.idea.test.findFileWithCaret
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractMultiPlatformCompletionTest : AbstractMultiModuleTest() {
    protected fun doTest(testPath: String) {
        val testFile = File(testPath)
        setupMppProjectFromDirStructure(testFile)
        val file = project.findFileWithCaret() as KtFile
        val virtualFilePath = file.virtualFile!!.toNioPath()

        IgnoreTests.runTestIfNotDisabledByFileDirective(virtualFilePath, IgnoreTests.DIRECTIVES.of(pluginMode)) {
            val doc = PsiDocumentManager.getInstance(myProject).getDocument(file)!!
            val offset = doc.extractMarkerOffset(project)
            val editor = EditorFactory.getInstance().createEditor(doc, myProject)!!
            editor.caretModel.moveToOffset(offset)
            try {
                testCompletion(file, editor)
            } finally {
                EditorFactory.getInstance().releaseEditor(editor)
            }
        }
    }

    private fun testCompletion(file: KtFile, editor: Editor) {
        testCompletion(file.text, null, { completionType, invocationCount ->
            CodeCompletionHandlerBase(completionType).invokeCompletion(
                myProject, InjectedLanguageUtil
                    .getEditorForInjectedLanguageNoCommit(editor, file), invocationCount
            )

            val lookup = LookupManager.getActiveLookup(editor) as LookupImpl
            lookup.items?.toTypedArray()
        })
    }

    override fun getTestDataDirectory() = COMPLETION_TEST_DATA_BASE.resolve("smartMultiFile").resolve(getTestName(false))
}