// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.postfix.test

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.artifacts.KotlinJvmLightProjectDescriptor
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.idea.util.application.executeCommand

/**
 * Regression test for [KTIJ-38065](https://youtrack.jetbrains.com/issue/KTIJ-38065): .var postfix completion changes `introduce variable#declare with var` setting
 * must not mutate the persistent `INTRODUCE_DECLARE_WITH_VAR` refactoring setting.
 * The postfix template and the Introduce Variable refactoring's default are conceptually independent.
 */
class KotlinIntroduceVariablePostfixTemplateSettingsTest : LightJavaCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinJvmLightProjectDescriptor.DEFAULT

    private val settings get() = KotlinCommonRefactoringSettings.getInstance()

    fun testVarPostfixDoesNotChangeIntroduceWithVarSettingWhenFalse() {
        doTest(initialSetting = false, fileText = "fun bar() = 1\nfun foo() { val x = bar().var<caret> }")
    }

    fun testVarPostfixDoesNotChangeIntroduceWithVarSettingWhenTrue() {
        doTest(initialSetting = true, fileText = "fun bar() = 1\nfun foo() { val x = bar().var<caret> }")
    }

    fun testValPostfixDoesNotChangeIntroduceWithVarSettingWhenTrue() {
        doTest(initialSetting = true, fileText = "fun bar() = 1\nfun foo() { val x = bar().val<caret> }")
    }

    fun testValPostfixDoesNotChangeIntroduceWithVarSettingWhenFalse() {
        doTest(initialSetting = false, fileText = "fun bar() = 1\nfun foo() { val x = bar().val<caret> }")
    }

    private fun doTest(initialSetting: Boolean, fileText: String) {
        val originalSetting = settings.INTRODUCE_DECLARE_WITH_VAR
        try {
            settings.INTRODUCE_DECLARE_WITH_VAR = initialSetting

            myFixture.configureByText("a.kt", fileText)
            myFixture.type("\t")
            NonBlockingReadActionImpl.waitForAsyncTaskCompletion()

            try {
                assertEquals(
                    "Postfix template expansion must not modify INTRODUCE_DECLARE_WITH_VAR",
                    initialSetting,
                    settings.INTRODUCE_DECLARE_WITH_VAR,
                )
            } finally {
                val templateState = TemplateManagerImpl.getTemplateState(editor)
                if (templateState?.isFinished == false) {
                    project.executeCommand("") { templateState.gotoEnd(false) }
                }
            }
        } finally {
            settings.INTRODUCE_DECLARE_WITH_VAR = originalSetting
        }
    }
}