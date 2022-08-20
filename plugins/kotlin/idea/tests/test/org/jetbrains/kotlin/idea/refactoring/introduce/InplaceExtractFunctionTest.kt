// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.ExtractKotlinFunctionHandler
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.junit.runner.RunWith

@RunWith(JUnit3RunnerWithInners::class)
class InplaceExtractFunctionTest : KotlinLightCodeInsightFixtureTestCase() {

    override val testDataDirectory = IDEA_TEST_DATA_DIR.resolve("refactoring/extractFunctionInplace")

    fun testStringTemplate() {
        doTest()
    }

    fun testStringTemplateWithNameConflict() {
        doTest(changedName = "substring")
    }

    fun testConflictNameNotAccepted() {
        doTest(changedName = "conflict", checkResult = false)
        TestCase.assertTrue(getActiveTemplate() != null)
    }

    fun testConsecutiveDuplicates() {
        doTest(changedName = "renamed")
    }

    fun testExposedAssignment() {
        doTest(changedName = "getX")
    }

    fun doTest(changedName: String? = null, checkResult: Boolean = true) {
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        myFixture.configureByFile("${getTestName(false)}.kt")
        ExtractKotlinFunctionHandler().invoke(myFixture.project, myFixture.editor, myFixture.file, null)
        val template = getActiveTemplate()
        require(template != null) { "Failed to start refactoring" }
        if (changedName != null) {
            renameTemplate(template, changedName)
        }
        template.gotoEnd(false)
        UIUtil.dispatchAllInvocationEvents()
        if (checkResult) {
            myFixture.checkResultByFile("${getTestName(false)}.after.kt")
        }
    }

    private fun getActiveTemplate() = TemplateManagerImpl.getTemplateState(editor)

    private fun renameTemplate(templateState: TemplateState, name: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            val range = templateState.currentVariableRange!!
            editor.document.replaceString(range.startOffset, range.endOffset, name)
        }
    }
}