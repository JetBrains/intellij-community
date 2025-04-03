// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.navigation

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.util.TextRange
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.IgnoreTests.DIRECTIVES
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.junit.Assert
import org.junit.ComparisonFailure
import java.nio.file.Path

abstract class AbstractGotoActionTest : KotlinLightCodeInsightFixtureTestCase() {
    protected abstract val actionName: String

    protected fun doTest(testPath: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(
            testFile = Path.of(testPath),
            disableTestDirective = DIRECTIVES.of(pluginMode)
        ) {
            val parts = KotlinTestUtils.loadBeforeAfterAndDependenciesText(testPath)

            val firstFile = parts.first()
            val lastFile = parts.last()

            for (index in 1 until (parts.size - 1)) {
                val testFile = parts[index]
                val fileType = FileTypeManager.getInstance().getFileTypeByExtension(testFile.name.substringAfterLast("."))
                myFixture.configureByText(fileType, testFile.content)
            }

            val firstFileType = FileTypeManager.getInstance().getFileTypeByExtension(firstFile.name.substringAfterLast("."))
            myFixture.configureByText(firstFileType, firstFile.content)
            performAction()

            val fileEditorManager = FileEditorManager.getInstance(myFixture.project) as FileEditorManagerEx
            val currentEditor = fileEditorManager.selectedTextEditor ?: editor

            if (currentEditor == editor) {
                val text = myFixture.getDocument(myFixture.file).text
                val afterText = StringBuilder(text).insert(editor.caretModel.offset, "<caret>").toString().trim()

                try {
                    Assert.assertEquals(lastFile.content.trim(), afterText)
                } catch (e: ComparisonFailure) {
                    throw FileComparisonFailedError(
                        e.message,
                        e.expected, e.actual, testPath, null
                    )
                }
            } else {
                val fileOffset = currentEditor.caretModel.offset
                val lineNumber = currentEditor.document.getLineNumber(fileOffset)
                val lineStart = currentEditor.document.getLineStartOffset(lineNumber)
                val lineEnd = currentEditor.document.getLineEndOffset(lineNumber)
                val inLineOffset = fileOffset - lineStart

                val line = currentEditor.document.getText(TextRange(lineStart, lineEnd))
                val withCaret = with(StringBuilder()) {
                    append(line)
                    insert(inLineOffset, "<caret>")
                    toString()
                }

                Assert.assertEquals(lastFile.content, withCaret)
            }
        }
    }

    protected open fun performAction() {
        myFixture.performEditorAction(actionName)
    }

    override fun getProjectDescriptor() = getProjectDescriptorFromTestName()
}
