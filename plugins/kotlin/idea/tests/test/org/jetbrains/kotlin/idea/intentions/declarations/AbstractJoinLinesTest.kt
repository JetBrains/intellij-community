// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions.declarations

import com.intellij.codeInsight.editorActions.JoinLinesHandler
import com.intellij.ide.DataManager
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import java.io.File

abstract class AbstractJoinLinesTest : KotlinLightCodeInsightFixtureTestCase() {
    @Throws(Exception::class)
    fun doTest(unused: String) {
        myFixture.configureByFile(fileName())

        val dataContext = DataManager.getInstance().getDataContext(editor.contentComponent)

        myFixture.project.executeWriteCommand("Join lines") {
            JoinLinesHandler(null).execute(editor, editor.caretModel.currentCaret, dataContext)
        }

        val testFile = dataFile()
        val expectedFile = File(testFile.parentFile, testFile.name + ".after")
        myFixture.checkResultByFile(expectedFile)
    }
}