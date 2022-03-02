// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.editor.commenter

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.formatter.FormatSettingsUtil
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.configureCodeStyleAndRun
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import java.io.File

abstract class AbstractKotlinCommenterTest : KotlinLightCodeInsightFixtureTestCase() {
    protected fun doTest(testPath: String) {
        val fileText = FileUtil.loadFile(File(testPath))
        configureCodeStyleAndRun(
            project = project,
            configurator = {
                FormatSettingsUtil.createConfigurator(fileText, it).configureSettings()
            },
            body = {
                doTest(fileText, testPath)
            }
        )
    }

    private fun doTest(text: String, testPath: String) {
        val action = InTextDirectivesUtils.findStringWithPrefixes(text, "// ACTION:")
            ?: error("'// ACTION:' directive is not found")

        val actionId = when (action) {
            "line" -> IdeActions.ACTION_COMMENT_LINE
            "block" -> IdeActions.ACTION_COMMENT_BLOCK
            else -> error("unexpected '$action' action")
        }

        myFixture.configureByFile(testPath)
        myFixture.performEditorAction(actionId)
        myFixture.checkResultByFile(File("$testPath.after"))
    }
}