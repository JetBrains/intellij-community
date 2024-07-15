// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.java.refactoring.ExtractMethodAndDuplicatesInplaceTest.Companion.nextTemplateVariable
import com.intellij.java.refactoring.ExtractMethodAndDuplicatesInplaceTest.Companion.renameTemplate
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.base.test.KotlinTestHelpers
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.AbstractExtractKotlinFunctionHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.ExtractKotlinFunctionHandler
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import java.io.File

abstract class AbstractInplaceIntroduceFunctionTest : KotlinLightCodeInsightFixtureTestCase() {
    fun doTest(unused: String) {
        val disableTestDirective = IgnoreTests.DIRECTIVES.of(pluginMode)

        IgnoreTests.runTestIfNotDisabledByFileDirective(
            dataFilePath(),
            disableTestDirective,
            directivePosition = IgnoreTests.DirectivePosition.LAST_LINE_IN_FILE
        ) { isTestEnabled -> doTestInternal() }
    }

    private fun doTestInternal() {
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        val mainFile = File(testDataDirectory, fileName())
        myFixture.testDataPath = mainFile.parent
        val file = myFixture.configureByFile(mainFile.name)
        getExtractFunctionHandler(getTestName(true).contains("Local")).invoke(myFixture.project, myFixture.editor, myFixture.file, null)
        KotlinTestHelpers.registerChooserInterceptor(myFixture.testRootDisposable)
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        val template = getActiveTemplate()
        require(template != null) { "Failed to start refactoring" }

        val fileText = file.text
        val changedName: String? = InTextDirectivesUtils.findListWithPrefixes(fileText, "// CHANGED_NAME: ").firstOrNull()
        val checkResult: Boolean = !InTextDirectivesUtils.isDirectiveDefined(fileText, "// IGNORE_RESULT")
        if (changedName != null) {
            renameTemplate(template, changedName)
        }
        nextTemplateVariable(template)
        UIUtil.dispatchAllInvocationEvents()
        if (checkResult) {
            val testName = getTestName(false)
            var expectedFile = "$testName.kt.after"
            if (isFirPlugin && File(testDataDirectory, "$testName.kt.fir.after").exists()) {
                expectedFile = "$testName.kt.fir.after"
            }

            myFixture.checkResultByFile(expectedFile)
        }
    }

    protected open fun getExtractFunctionHandler(allContainersEnabled: Boolean): AbstractExtractKotlinFunctionHandler =
        ExtractKotlinFunctionHandler(allContainersEnabled)

    private fun getActiveTemplate() = TemplateManagerImpl.getTemplateState(editor)
}