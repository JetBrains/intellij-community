// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.editor

import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.util.slashedPath
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class StripTrailingSpacesTest : LightJavaCodeInsightFixtureTestCase() {
    override fun getTestDataPath() = IDEA_TEST_DATA_DIR.resolve("editor/stripTrailingSpaces").slashedPath

    fun testKeepTrailingSpacesInRawString() {
        doTest()
    }

    fun doTest() {
        myFixture.configureByFile("${getTestName(true)}.kt")

        val editorSettings = EditorSettingsExternalizable.getInstance()
        val stripSpaces = editorSettings.stripTrailingSpaces
        try {
            editorSettings.stripTrailingSpaces = EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE
            val doc = myFixture.editor.document
            EditorTestUtil.performTypingAction(editor, ' ')
            PsiDocumentManager.getInstance(project).commitDocument(doc)
            FileDocumentManager.getInstance().saveDocument(doc)
        } finally {
            editorSettings.stripTrailingSpaces = stripSpaces
        }

        myFixture.checkResultByFile("${getTestName(true)}.kt.after")
    }
}
